package practice.aiassisted.streaming;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class ReadWriteLockLogger implements AutoCloseable {
    private static final int CAPACITY = 1000;
    private static final int RETRY_ATTEMPTS = 3;
    private static final int BACKOFF_MILLIS = 2;
    private static final long FLUSH_INTERVAL_NANOS = TimeUnit.MILLISECONDS.toNanos(100);

    private final ISink sink;
    private final String[] buffer;
    private final AtomicInteger size;
    private final ReentrantReadWriteLock bufferLock;
    private final Lock readLock;
    private final Lock writeLock;
    private final ReentrantLock coordinationLock;
    private final Condition flushNeeded;
    private final Condition spaceAvailable;
    private final Thread flusherThread;
    private volatile boolean closed;
    private volatile boolean failed;

    public ReadWriteLockLogger() {
        this(new MockFileSink("dummy", 0.3));
    }

    ReadWriteLockLogger(ISink sink) {
        this.sink = sink;
        buffer = new String[CAPACITY];
        size = new AtomicInteger();
        bufferLock = new ReentrantReadWriteLock(true);
        readLock = bufferLock.readLock();
        writeLock = bufferLock.writeLock();
        coordinationLock = new ReentrantLock();
        flushNeeded = coordinationLock.newCondition();
        spaceAvailable = coordinationLock.newCondition();
        flusherThread = new Thread(this::flushLoop, "read-write-lock-logger-flusher");
        flusherThread.setDaemon(true);
        flusherThread.start();
    }

    public boolean ingest(String log) {
        while (!closed && !failed) {
            int index;
            readLock.lock();
            try {
                if (closed || failed) {
                    return false;
                }
                index = reserveSlot();
                if (index >= 0) {
                    buffer[index] = log;
                }
            } finally {
                readLock.unlock();
            }

            if (index >= 0) {
                if (index == 0 || index == CAPACITY - 1) {
                    signalFlushNeeded();
                }
                return true;
            }

            signalFlushNeeded();
            if (!awaitSpaceAvailable()) {
                return false;
            }
        }
        return false;
    }

    private int reserveSlot() {
        while (true) {
            int currentSize = size.get();
            if (currentSize == CAPACITY) {
                return -1;
            }
            if (size.compareAndSet(currentSize, currentSize + 1)) {
                return currentSize;
            }
        }
    }

    private void flushLoop() {
        while (true) {
            coordinationLock.lock();
            try {
                while (size.get() == 0 && !closed) {
                    flushNeeded.await();
                }
                if (size.get() == 0 && closed) {
                    return;
                }

                long deadline = System.nanoTime() + FLUSH_INTERVAL_NANOS;
                while (size.get() < CAPACITY && !closed) {
                    long remaining = deadline - System.nanoTime();
                    if (remaining <= 0) {
                        break;
                    }
                    flushNeeded.awaitNanos(remaining);
                }
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return;
            } finally {
                coordinationLock.unlock();
            }

            writeLock.lock();
            try {
                int batchSize = size.get();
                if (batchSize == 0) {
                    if (closed) {
                        return;
                    }
                    continue;
                }

                List<String> batch = new ArrayList<>(batchSize);
                for (int index = 0; index < batchSize; index++) {
                    batch.add(buffer[index]);
                }

                if (!writeWithRetries(batch)) {
                    failed = true;
                    signalSpaceAvailable();
                    return;
                }

                Arrays.fill(buffer, 0, batchSize, null);
                size.set(0);
            } finally {
                writeLock.unlock();
            }
            signalSpaceAvailable();
        }
    }

    private boolean awaitSpaceAvailable() {
        coordinationLock.lock();
        try {
            while (size.get() == CAPACITY && !closed && !failed) {
                spaceAvailable.await();
            }
            return !closed && !failed;
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            return false;
        } finally {
            coordinationLock.unlock();
        }
    }

    private void signalFlushNeeded() {
        coordinationLock.lock();
        try {
            flushNeeded.signal();
        } finally {
            coordinationLock.unlock();
        }
    }

    private void signalSpaceAvailable() {
        coordinationLock.lock();
        try {
            spaceAvailable.signalAll();
        } finally {
            coordinationLock.unlock();
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
        closed = true;
        writeLock.lock();
        writeLock.unlock();
        coordinationLock.lock();
        try {
            flushNeeded.signalAll();
            spaceAvailable.signalAll();
        } finally {
            coordinationLock.unlock();
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
        int low = BACKOFF_MILLIS * (int) (Math.pow(2, attempt));
        int high = BACKOFF_MILLIS * (int) (Math.pow(2, attempt + 1));
        return low + ThreadLocalRandom.current().nextInt(high);
    }
}
