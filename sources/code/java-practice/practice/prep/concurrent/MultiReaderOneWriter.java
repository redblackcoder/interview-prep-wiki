package practice.prep.concurrent;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Logger;

public class MultiReaderOneWriter {
    private static final Logger LOGGER = Logger.getLogger(MultiReaderOneWriter.class.getName());

    private final Map<String, String> record;
    private final AtomicInteger numReader;
    private final Lock readLock;
    private final Condition firstReaderBusy;
    private final Semaphore writeLock;
    private final AtomicBoolean writesLocked;

    public MultiReaderOneWriter() {
        record = new HashMap<>();
        numReader = new AtomicInteger(0);
        readLock = new ReentrantLock();
        firstReaderBusy = readLock.newCondition();
        writeLock = new Semaphore(1);
        writesLocked = new AtomicBoolean(false);
    }

    public Map<String, String> read(Set<String> projections) {
        while (true) {
            int prev = numReader.get();
            if (numReader.compareAndSet(0, 1)) {
                enterFirstReader();
                break;
            } else {
                if (numReader.compareAndSet(prev, prev + 1)) {
                    break;
                }
            }
        }

        while (!writesLocked.get()) {
            LOGGER.info(String.format("Thread %s entering to wait on firstReaderBusy condition because writesLocked is false.%n", Thread.currentThread().getName()));
            try {
                if (!readLock.tryLock(1, TimeUnit.MICROSECONDS)) {
                    continue;
                }
            } catch (InterruptedException intExp) {
                LOGGER.info(String.format("Thread %s interrupted.%n", Thread.currentThread().getName()));
            }

            if (writesLocked.get()) {
                break;
            }

            try {
                LOGGER.info(String.format("Thread %s is going to wait on firstReaderBusy condition.%n", Thread.currentThread().getName()));
                firstReaderBusy.await();
                LOGGER.info(String.format("Thread %s awake from firstReaderBusy condition.%n", Thread.currentThread().getName()));
            } catch (InterruptedException intExp) {
                LOGGER.info(String.format("Thread %s interrupted.%n", Thread.currentThread().getName()));
            } finally {
                readLock.unlock();
            }
        }

        // Do work
        Map<String, String> result = new HashMap<>();
        projections.forEach(k -> Optional.ofNullable(record.get(k)).ifPresent(v -> result.put(k, v)));

        while (true) {
            int prev = numReader.get();
            if (numReader.compareAndSet(1, 0)) {
                leaveLastReader();
                break;
            } else {
                if (numReader.compareAndSet(prev, prev - 1)) {
                    break;
                }
            }
        }

        return result;
    }

    public void write(Map<String, String> values) {
        LOGGER.info(String.format("Thread %s entered to write values %s.%n", Thread.currentThread().getName(), values));
        try {
            while (!writeLock.tryAcquire(1, TimeUnit.MICROSECONDS));
            values.forEach((k, v) -> record.put(k, v));
        } catch (InterruptedException intExp) {
            LOGGER.info(String.format("Thread %s interrupted.%n", Thread.currentThread().getName()));
        } finally {
            writeLock.release();
        }

        LOGGER.info(String.format("Thread %s finished writing values.%n", Thread.currentThread().getName()));
    }

    private void enterFirstReader() {
        LOGGER.info(String.format("Thread %s entered as first reader.%n", Thread.currentThread().getName()));
        readLock.lock();
        try {
            while (!writeLock.tryAcquire(1, TimeUnit.MICROSECONDS));
            writesLocked.set(true);
            firstReaderBusy.signalAll();
            LOGGER.info(String.format("Thread %s signalled firstReaderBusy and set writesLocked to true.%n", Thread.currentThread().getName()));
        } catch (InterruptedException intExp) {
            LOGGER.info(String.format("Thread %s interrupted.%n", Thread.currentThread().getName()));
        } finally {
            readLock.unlock();
        }
    }

    private void leaveLastReader() {
        LOGGER.info(String.format("Thread %s leaving as last reader.%n", Thread.currentThread().getName()));
        readLock.lock();
        try {
            writesLocked.set(false);
            writeLock.release();
            LOGGER.info(String.format("Thread %s is the last reader to leave and set writesLocked to false.%n", Thread.currentThread().getName()));
        } finally {
            readLock.unlock();
        }
    }
}
