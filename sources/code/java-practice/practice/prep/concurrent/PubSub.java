package practice.prep.concurrent;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.*;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

/**
 * Support a single producers per topic and multiple concurrent consumers
 */
public class PubSub {
    private final int max;
    private final Map<String, Messages> messagesByTopic;
    private final ExecutorService executor;

    public PubSub() {
        this(1000);
    }

    public PubSub(int max) {
        this.max = max;
        messagesByTopic = new HashMap<>();
        executor = Executors.newCachedThreadPool();
    }

    public synchronized boolean registerTopic(String topic) {
        if (messagesByTopic.containsKey(topic)) {
            System.out.println(String.format("Topic %s already registered and in use.", topic));
            return false;
        }

        messagesByTopic.put(topic, new Messages(max));
        return true;
    }

    public boolean subscribe(String topic, Consumer<String> consumer) {
        synchronized(messagesByTopic) {
            if (!messagesByTopic.containsKey(topic)) {
                System.out.println(String.format("Topic %s doesn't exists.", topic));
                return false;
            }
        }

        executor.submit(() -> pushToSubscriber(topic, consumer));
        return true;
    }

    public boolean publish(String topic, String message) {
        synchronized(messagesByTopic) {
            if (!messagesByTopic.containsKey(topic)) {
                return false;
            }
        }

        final Messages messages;
        synchronized(messagesByTopic) {
            messages = messagesByTopic.get(topic);
        }

        messages.publish(message);
        messages.lock.lock();
        try {
            messages.condition.signalAll();
        } finally {
            messages.lock.unlock();
        }

        return true;
    }

    private void pushToSubscriber(String topic, Consumer<String> consumer) {
        final Messages messages = messagesByTopic.get(topic);
        long lastPushed = -1;
        while (true) {
            if (lastPushed == messages.leastMessageId.get() + Math.min(messages.messageList.size(), messages.maxCapacity)) {
                messages.lock.lock();
                try {
                    messages.condition.await();
                } catch (InterruptedException intExp) {
                    Thread.currentThread().interrupt();
                } finally {
                    messages.lock.unlock();
                }

                continue;
            }

            for (long i = Math.max(messages.leastMessageId.get(), lastPushed + 1); i < messages.leastMessageId.get() + Math.min(messages.messageList.size(), messages.maxCapacity); i++) {
                if (i < messages.leastMessageId.get()) {
                    continue;
                }

                int idx = i > messages.maxCapacity ? (int)(i % messages.messageList.size()) : (int)i;
                String message = messages.messageList.get(idx);
                try {
                    consumer.accept(message);
                } catch (Exception e) {
                    System.err.printf("Error pushing message %s to consumer. Exception: %s", message, e);
                } finally {
                    lastPushed = i;
                }
            }
        }
    }

    /**
     * maxCapacity: 4
     * size: 5
     * messageList: [0, 1, 2, 3, 4]
     * leastMessageId: 2
     */
    private static class Messages {
        private final Lock lock;
        private final Condition condition;
        private final int maxCapacity;
        private final List<String> messageList;
        private final AtomicLong leastMessageId;

        private Messages(int maxCapacity) {
            this.lock = new ReentrantLock();
            condition = this.lock.newCondition();
            this.maxCapacity = maxCapacity;
            messageList = new ArrayList<>(maxCapacity + 1);
            leastMessageId = new AtomicLong(-1);
        }

        private void publish(String message) {
            if (messageList.size() < maxCapacity) {
                messageList.add(message);
                if (messageList.size() == 1) {
                    leastMessageId.set(0);
                }
            } else {
                if (messageList.size() == maxCapacity) {
                    messageList.add(message);
                } else {
                    messageList.set((int)((leastMessageId.get() + maxCapacity)%(maxCapacity + 1)), message);
                }

                leastMessageId.incrementAndGet();
            }
        }
    }
}
