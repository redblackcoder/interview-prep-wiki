package practice.aiassisted.streaming;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Phaser;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.LockSupport;
import java.util.concurrent.locks.ReentrantLock;

public class LockFreeLogger implements AutoCloseable {
    private static final int CAPACITY = 1000;
    private static final int RETRY_ATTEMPTS = 3;
    private static final int BACKOFF_MILLIS = 2;
    private static final long FLUSH_INTERVAL_NANOS = TimeUnit.MILLISECONDS.toNanos(100);

    private final ISink sink;
    private final String[] buffer;
    private final AtomicInteger size;
    private final Phaser writerPhaser;
    private final AtomicBoolean flushing;
    private final ReentrantLock waitLock;
    private final Condition bufferAvailable;
    private final Thread flusherThread;
    private volatile boolean closed;
    private volatile boolean failed;

    public LockFreeLogger() {
        this(new MockFileSink("dummy", 0.3));
    }

    LockFreeLogger(ISink sink) {
        this.sink = sink;
        buffer = new String[CAPACITY];
        size = new AtomicInteger();
        writerPhaser = new Phaser(1);
        flushing = new AtomicBoolean();
        waitLock = new ReentrantLock();
        bufferAvailable = waitLock.newCondition();
        flusherThread = new Thread(this::flushLoop, "lock-free-logger-buffer-flusher");
        flusherThread.setDaemon(true);
        flusherThread.start();
    }

    public boolean ingest(String log) {
        while (enterWriter()) {
            int index = reserveSlot();
            if (index >= 0) {
                buffer[index] = log;
                if (index == 0 || index == CAPACITY - 1) {
                    LockSupport.unpark(flusherThread);
                }
                exitWriter();
                return true;
            }

            exitWriter();
            LockSupport.unpark(flusherThread);
            if (!awaitBufferAvailable()) {
                return false;
            }
        }
        return false;
    }

    private boolean enterWriter() {
        while (!closed && !failed) {
            if (flushing.get()) {
                if (!awaitBufferAvailable()) {
                    return false;
                }
                continue;
            }
            writerPhaser.register();
            if (!flushing.get() && !closed && !failed) {
                return true;
            }
            exitWriter();
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

    private void exitWriter() {
        writerPhaser.arriveAndDeregister();
    }

    private boolean awaitBufferAvailable() {
        waitLock.lock();
        try {
            while (!closed && !failed
                    && (size.get() == CAPACITY || flushing.get())) {
                bufferAvailable.await();
            }
            return !closed && !failed;
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            return false;
        } finally {
            waitLock.unlock();
        }
    }

    private void flushLoop() {
        boolean deadlineSet = false;
        long deadline = 0;

        while (true) {
            int currentSize = size.get();
            if (closed) {
                beginFlush();
                currentSize = size.get();
                if (currentSize == 0) {
                    return;
                }
            } else {
            if (currentSize == 0) {
                deadlineSet = false;
                LockSupport.park(this);
                continue;
            }

            if (currentSize < CAPACITY && !closed) {
                if (!deadlineSet) {
                    deadline = System.nanoTime() + FLUSH_INTERVAL_NANOS;
                    deadlineSet = true;
                }
                long remaining = deadline - System.nanoTime();
                if (remaining > 0) {
                    LockSupport.parkNanos(this, remaining);
                    continue;
                }
            }

            beginFlush();
            currentSize = size.get();
            if (currentSize == 0) {
                finishFlush();
                deadlineSet = false;
                continue;
            }
            }

            int batchSize = currentSize;
            List<String> batch = new ArrayList<>(batchSize);
            for (int index = 0; index < batchSize; index++) {
                batch.add(buffer[index]);
            }

            if (!writeWithRetries(batch)) {
                failed = true;
                flushing.set(false);
                signalBufferAvailable();
                return;
            }

            Arrays.fill(buffer, 0, batchSize, null);
            size.set(0);
            finishFlush();
            deadlineSet = false;
        }
    }

    private void beginFlush() {
        flushing.set(true);
        writerPhaser.arriveAndAwaitAdvance();
    }

    private void finishFlush() {
        flushing.set(false);
        signalBufferAvailable();
    }

    private void signalBufferAvailable() {
        waitLock.lock();
        try {
            bufferAvailable.signalAll();
        } finally {
            waitLock.unlock();
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
        LockSupport.unpark(flusherThread);
        signalBufferAvailable();

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
