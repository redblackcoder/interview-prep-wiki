package practice.aiassisted.streaming;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class Logger implements AutoCloseable {
    private static final int CAPACITY = 1000;
    private static final int RETRY_ATTEMPTS = 3;
    private static final int BACKOFF_MILLIS = 2;
    private static final long FLUSH_INTERVAL_MILLIS = 100;

    private final ISink sink;
    private final String[] buffer;
    private int size;
    private final ReentrantLock lock;
    private final Condition flushNeeded;
    private final Condition bufferReset;
    private final Thread flusherThread;
    private boolean closed;
    private boolean flushing;
    private boolean failed;

    public Logger() {
        this(new MockFileSink("dummy", 0.3));
    }

    Logger(ISink sink) {
        this.sink = sink;
        buffer = new String[CAPACITY];
        size = 0;
        lock = new ReentrantLock();
        flushNeeded = lock.newCondition();
        bufferReset = lock.newCondition();
        flusherThread = new Thread(this::flushLoop, "logger-buffer-flusher");
        flusherThread.setDaemon(true);
        flusherThread.start();
    }

    public boolean ingest(String log) {
        lock.lock();
        try {
            while ((size == CAPACITY || flushing) && !closed && !failed) {
                bufferReset.await();
            }
            if (closed || failed) {
                return false;
            }
            buffer[size++] = log;
            if (size == 1 || size == CAPACITY) {
                flushNeeded.signal();
            }
            return true;
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            System.out.println("Ingestion interrupted. Exiting.");
            return false;
        } finally {
            lock.unlock();
        }
    }

    private void flushLoop() {
        while (true) {
            List<String> batch;
            lock.lock();
            try {
                long deadline = System.nanoTime() + FLUSH_INTERVAL_MILLIS * 1000_000;
                while (size < CAPACITY && !closed) {
                    if (size == 0) {
                        flushNeeded.await();
                        if (size > 0) {
                            deadline = System.nanoTime() + FLUSH_INTERVAL_MILLIS * 1000_000;
                        }
                    } else {
                        long remaining = deadline - System.nanoTime();
                        if (remaining <= 0) {
                            break;
                        }

                        flushNeeded.awaitNanos(remaining);
                    }
                }

                if (size == 0 && closed) {
                    return;
                }

                flushing = true;
                batch = new ArrayList<>(size);
                for (int index = 0; index < size; index++) {
                    batch.add(buffer[index]);
                }
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return;
            } finally {
                lock.unlock();
            }

            if (!writeWithRetries(batch)) {
                lock.lock();
                try {
                    failed = true;
                    flushing = false;
                    bufferReset.signalAll();
                } finally {
                    lock.unlock();
                }
                return;
            }

            lock.lock();
            try {
                Arrays.fill(buffer, null);
                size = 0;
                flushing = false;
                bufferReset.signalAll();
            } finally {
                lock.unlock();
            }
        }
    }

    private boolean writeWithRetries(List<String> batch) {
        for (int attempt = 1; attempt <= RETRY_ATTEMPTS; attempt++) {
            try {
                sink.write(batch);
                return true;
            } catch (TransientStorageException tse) {
                System.err.println("Attempt " + attempt + " failed to write buffer to sink.");
                if (attempt < RETRY_ATTEMPTS) {
                    try {
                        Thread.sleep(backoff(attempt));
                    } catch (InterruptedException ie) {
                        Thread.interrupted();
                    }
                }
            }
        }
        System.err.println("Failed to flush buffer in " + RETRY_ATTEMPTS + " attempts. Buffer retained.");
        return false;
    }

    @Override
    public void close() {
        lock.lock();
        try {
            closed = true;
            flushNeeded.signal();
            bufferReset.signalAll();
        } finally {
            lock.unlock();
        }

        if (Thread.currentThread() != flusherThread) {
            try {
                flusherThread.join();
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private int backoff(int attempt) {
        int low = BACKOFF_MILLIS * (int)(Math.pow(2, attempt));
        int high = BACKOFF_MILLIS * (int)(Math.pow(2, attempt + 1));
        return low + ThreadLocalRandom.current().nextInt(high);
    }
}
