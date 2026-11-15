package practice.prep.concurrent;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.*;
import java.util.logging.Logger;
import java.util.function.Consumer;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ExecutorService;

/**
 * Simple blocking single publisher and single subscriber queue
 */
public class BlockingPubSub {
    private static final Logger LOGGER = Logger.getLogger(BlockingPubSub.class.getName());

    private final int max;
    private final Map<String, AtomicReference<Buffer>> bufferByTopic;
    private final ExecutorService executor;
    private final Map<String, Future> subscribedFutures;

    public BlockingPubSub() {
        this(1000);
    }

    public BlockingPubSub(int max) {
        this.max = max;
        bufferByTopic = new HashMap<>();
        executor = Executors.newCachedThreadPool();
        subscribedFutures = new HashMap<>();
    }

    public boolean register(String topic) {
        AtomicReference<Buffer> topicBuffer = bufferByTopic.computeIfAbsent(topic, k -> new AtomicReference<Buffer>());
        if (topicBuffer.compareAndSet(null, new Buffer(topic, max))) {
            return true;
        }

        LOGGER.warning(String.format("Failed to register for topic %s sicne it is already registered", topic));
        return false;
    }

    public void publish(String topic, String message) {
        AtomicReference<Buffer> bufferRef = bufferByTopic.get(topic);
        if (bufferRef == null || bufferRef.get() == null) {
            LOGGER.info(String.format("Topic (%s) isn't registered yet.", topic));
            return;
        }

        bufferRef.get().addMessage(message);
    }

    public void subscribe(String topic, Consumer<String> consumer) {
        AtomicReference<Buffer> bufferRef = bufferByTopic.get(topic);
        if (bufferRef == null || bufferRef.get() == null) {
            LOGGER.info(String.format("Topic %s isn't registered yet.", topic));
            return;
        }

        if (!bufferRef.get().subscribed.compareAndSet(false, true)) {
            LOGGER.info(String.format("Topic %s is already subscribed, cannot have more than one subscriber.", topic));
            return;
        }

        Future<?> future = executor.submit(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                Optional<String> message = bufferRef.get().getMessage();
                if (!Thread.currentThread().isInterrupted()) {
                    message.ifPresent(consumer);
                }
            }
        });

        subscribedFutures.put(topic, future);
    }

    public void unsubscribe(String topic) {
        subscribedFutures.computeIfPresent(topic, (k, v) -> {
            v.cancel(true);
            return v;
        });
    }

    private static class Buffer {
        private final String topic;
        private final AtomicBoolean subscribed;
        private final String[] messages;
        private final int size;
        private final Lock mutex;
        private final Condition full;
        private final Condition empty;

        private int in;
        private int out;

        private Buffer(String topic, int size) {
            this.topic = topic;
            subscribed = new AtomicBoolean(false);
            messages = new String[size];
            this.size = size;
            mutex = new ReentrantLock();
            full = mutex.newCondition();
            empty = mutex.newCondition();

            in = 0;
            out = 0;
        }

        private void addMessage(String message) {
            try {
                mutex.lock();
                while ((in + 1)%size == out) {
                    LOGGER.info(String.format("Buffer for topic %s is full when trying to add message from thread: %s", topic, Thread.currentThread().getName()));
                    full.await();
                }

                messages[in] = message;
                in = (in + 1)%size;
                empty.signalAll();
            } catch (InterruptedException intExp) {
                LOGGER.info(String.format("Thread (%s) interrupted.", Thread.currentThread().getName()));
                Thread.currentThread().interrupt();
            } finally {
                mutex.unlock();
            }
        }

        private Optional<String> getMessage() {
            String message = null;
            boolean readMessage = false;
            try {
                mutex.lock();
                while (out == in) {
                    LOGGER.info(String.format("Buffer for topic %s is empty when trying to read message from thread: %s", topic, Thread.currentThread().getName()));
                    empty.await();
                }

                message = messages[out];
                readMessage = true;
                out = (out + 1)%size;
                full.signalAll();
            } catch (InterruptedException intExp) {
                LOGGER.info(String.format("Thread (%s) interrupted.", Thread.currentThread().getName()));
                Thread.currentThread().interrupt();
            } finally {
                mutex.unlock();
            }

            return readMessage ? Optional.of(message) : Optional.empty();
        }
    }
}
