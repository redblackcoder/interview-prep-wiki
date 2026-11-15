package com.practice.concurrent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.LockSupport;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

// A lost wake-up shows up as a hang; fail the test instead of blocking the build.
@Timeout(10)
public class BoundedQueueTest {

    // Stress tests: each value is producer * TAG + seq, so a consumer can tell who
    // produced it and in which order.
    private static final int PRODUCERS = 4;
    private static final int CONSUMERS = 4;
    private static final int PER_PRODUCER = 20_000;
    private static final int TAG = 1_000_000;
    private static final int POISON = Integer.MAX_VALUE;

    // ---- single thread ----

    @Test
    public void dequeuesInFifoOrder() {
        var q = new BoundedQueue(5);
        IntStream.rangeClosed(1, 5).forEach(i -> assertTrue(q.enqueue(i)));
        IntStream.rangeClosed(1, 5).forEach(i -> assertEquals(i, q.dequeue()));
    }

    @Test
    public void preservesFifoOrderAcrossWrapAround() {
        var q = new BoundedQueue(3);
        assertTrue(q.enqueue(1));
        assertTrue(q.enqueue(2));
        // Keep the queue 2-3 deep while head/tail lap the buffer hundreds of times.
        for (int v = 3; v <= 1_000; v++) {
            assertTrue(q.enqueue(v));
            assertEquals(v - 2, q.dequeue());
        }
        assertEquals(999, q.dequeue());
        assertEquals(1_000, q.dequeue());
    }

    // ---- blocking ----

    @Test
    public void enqueueBlocksWhileFullUntilDequeueFreesSlot() throws Exception {
        var q = new BoundedQueue(5);
        IntStream.rangeClosed(1, 5).forEach(i -> assertTrue(q.enqueue(i)));

        var producer = async(() -> q.enqueue(6));
        producer.awaitBlocked();

        assertEquals(1, q.dequeue());
        assertTrue(producer.get());
        IntStream.rangeClosed(2, 6).forEach(i -> assertEquals(i, q.dequeue()));
    }

    @Test
    public void dequeueBlocksWhileEmptyUntilEnqueue() throws Exception {
        var q = new BoundedQueue(5);

        var consumer = async(q::dequeue);
        consumer.awaitBlocked();

        assertTrue(q.enqueue(42));
        assertEquals(42, consumer.get());
    }

    @Test
    public void eachDequeueAdmitsExactlyOneBlockedProducer() throws Exception {
        var q = new BoundedQueue(1);
        assertTrue(q.enqueue(1));
        var p10 = async(() -> q.enqueue(10));
        var p20 = async(() -> q.enqueue(20));
        p10.awaitBlocked();
        p20.awaitBlocked();

        assertEquals(1, q.dequeue());
        var admitted = firstDone(p10, p20);
        var waiting = admitted == p10 ? p20 : p10;
        assertTrue(admitted.get());
        waiting.awaitBlocked(); // capacity 1: the freed slot went to exactly one producer

        assertEquals(admitted == p10 ? 10 : 20, q.dequeue());
        assertTrue(waiting.get());
        assertEquals(admitted == p10 ? 20 : 10, q.dequeue());
    }

    @Test
    public void eachEnqueueWakesExactlyOneBlockedConsumer() throws Exception {
        var q = new BoundedQueue(2);
        var c1 = async(q::dequeue);
        var c2 = async(q::dequeue);
        c1.awaitBlocked();
        c2.awaitBlocked();

        assertTrue(q.enqueue(7));
        var served = firstDone(c1, c2);
        var waiting = served == c1 ? c2 : c1;
        assertEquals(7, served.get());
        waiting.awaitBlocked(); // the single item was handed out once, not twice

        assertTrue(q.enqueue(8));
        assertEquals(8, waiting.get());
    }

    // ---- interrupts: the only way enqueue returns false / dequeue returns -1 ----

    @Test
    public void enqueueOnFullQueueReturnsFalseIfAlreadyInterrupted() throws Exception {
        var q = new BoundedQueue(5);
        IntStream.rangeClosed(1, 5).forEach(i -> assertTrue(q.enqueue(i)));

        var producer = async(() -> {
            Thread.currentThread().interrupt();
            return q.enqueue(6);
        });
        assertFalse(producer.get());
        assertTrue(producer.interruptedOnReturn(), "interrupt status should be preserved");

        assertEquals(1, q.dequeue());
        assertTrue(q.enqueue(6));
        IntStream.rangeClosed(2, 6).forEach(i -> assertEquals(i, q.dequeue()));
    }

    @Test
    public void dequeueOnEmptyQueueReturnsMinusOneIfAlreadyInterrupted() throws Exception {
        var q = new BoundedQueue(5);

        var consumer = async(() -> {
            Thread.currentThread().interrupt();
            return q.dequeue();
        });
        assertEquals(-1, consumer.get());
        assertTrue(consumer.interruptedOnReturn(), "interrupt status should be preserved");
    }

    @Test
    public void interruptingBlockedEnqueueReturnsFalseAndLeavesQueueIntact() throws Exception {
        var q = new BoundedQueue(2);
        assertTrue(q.enqueue(1));
        assertTrue(q.enqueue(2));

        var producer = async(() -> q.enqueue(3));
        producer.awaitBlocked();
        producer.interrupt();

        assertFalse(producer.get());
        assertTrue(producer.interruptedOnReturn(), "interrupt status should be preserved");
        assertEquals(1, q.dequeue());
        assertEquals(2, q.dequeue());
        assertTrue(q.enqueue(4));
        assertEquals(4, q.dequeue()); // 3 was never inserted
    }

    @Test
    public void interruptingBlockedDequeueReturnsMinusOneAndLeavesQueueIntact() throws Exception {
        var q = new BoundedQueue(2);

        var consumer = async(q::dequeue);
        consumer.awaitBlocked();
        consumer.interrupt();

        assertEquals(-1, consumer.get());
        assertTrue(consumer.interruptedOnReturn(), "interrupt status should be preserved");
        assertTrue(q.enqueue(5));
        assertEquals(5, q.dequeue());
    }

    @Test
    public void interruptDoesNotCancelOperationsThatNeedNoWait() throws Exception {
        var q = new BoundedQueue(2);

        var producer = async(() -> {
            Thread.currentThread().interrupt();
            return q.enqueue(9); // queue has room
        });
        assertTrue(producer.get());
        assertTrue(producer.interruptedOnReturn(), "interrupt status should be preserved");

        var consumer = async(() -> {
            Thread.currentThread().interrupt();
            return q.dequeue(); // queue has an item
        });
        assertEquals(9, consumer.get());
        assertTrue(consumer.interruptedOnReturn(), "interrupt status should be preserved");
    }

    // ---- many threads ----

    @ParameterizedTest(name = "capacity {0}")
    @ValueSource(ints = {1, 4})
    public void concurrentProducersAndConsumersTransferEveryValueExactlyOnceInOrder(int capacity)
            throws Exception {
        runProducersAndConsumers(new BoundedQueue(capacity), false);
    }

    @Test
    public void concurrentProducersAndConsumersSurviveRandomInterrupts() throws Exception {
        runProducersAndConsumers(new BoundedQueue(2), true);
    }

    /**
     * Producers each enqueue PER_PRODUCER tagged values; consumers drain until POISON.
     * Checks every value arrives exactly once and each consumer sees a given producer's
     * values in the order they were enqueued. With {@code chaos}, workers are interrupted
     * at random and retry whatever operation the interrupt cancelled.
     */
    private static void runProducersAndConsumers(BoundedQueue q, boolean chaos) throws Exception {
        var producers = new ArrayList<Thread>();
        var consumers = new ArrayList<Thread>();
        var received = new ArrayList<List<Integer>>();
        var cancelled = new AtomicInteger();

        for (int p = 0; p < PRODUCERS; p++) {
            int base = p * TAG;
            producers.add(daemon(() -> {
                for (int seq = 1; seq <= PER_PRODUCER; seq++) {
                    while (!q.enqueue(base + seq)) {
                        cancelled.incrementAndGet();
                        Thread.interrupted(); // clear, then retry the same value
                    }
                }
            }));
        }
        for (int c = 0; c < CONSUMERS; c++) {
            var mine = new ArrayList<Integer>();
            received.add(mine);
            consumers.add(daemon(() -> {
                while (true) {
                    int v = q.dequeue();
                    if (v == -1) {
                        cancelled.incrementAndGet();
                        Thread.interrupted(); // clear, then retry
                    } else if (v == POISON) {
                        return;
                    } else {
                        mine.add(v);
                    }
                }
            }));
        }

        var workers = new ArrayList<Thread>(producers);
        workers.addAll(consumers);
        var stopChaos = new AtomicBoolean();
        var interrupter = daemon(() -> {
            while (!stopChaos.get()) {
                workers.get(ThreadLocalRandom.current().nextInt(workers.size())).interrupt();
                LockSupport.parkNanos(20_000);
            }
        });

        workers.forEach(Thread::start);
        if (chaos) {
            interrupter.start();
        }
        try {
            for (Thread t : producers) {
                t.join();
            }
        } finally {
            stopChaos.set(true);
        }
        interrupter.join();
        for (int c = 0; c < CONSUMERS; c++) {
            assertTrue(q.enqueue(POISON));
        }
        for (Thread t : consumers) {
            t.join();
        }

        var seen = new BitSet();
        for (List<Integer> values : received) {
            int[] lastSeq = new int[PRODUCERS];
            for (int v : values) {
                int p = v / TAG, seq = v % TAG;
                assertTrue(p < PRODUCERS && seq >= 1 && seq <= PER_PRODUCER, "unexpected value " + v);
                assertFalse(seen.get(v), "value dequeued twice: " + v);
                assertTrue(seq > lastSeq[p],
                        "FIFO violated for producer " + p + ": " + seq + " after " + lastSeq[p]);
                seen.set(v);
                lastSeq[p] = seq;
            }
        }
        assertEquals(PRODUCERS * PER_PRODUCER, seen.cardinality(), "values lost");
        if (chaos) {
            assertTrue(cancelled.get() > 0, "interrupts never cancelled an operation; chaos had no effect");
        } else {
            assertEquals(0, cancelled.get(), "an operation gave up without being interrupted");
        }
    }

    // ---- helpers ----

    private static Thread daemon(Runnable body) {
        var t = new Thread(body);
        t.setDaemon(true);
        return t;
    }

    /** Runs {@code op} on a new thread so the test thread can watch it block. */
    private static <T> Worker<T> async(Callable<T> op) {
        return new Worker<>(op);
    }

    /** Waits for whichever worker finishes first and returns it. */
    private static <T> Worker<T> firstDone(Worker<T> a, Worker<T> b) throws Exception {
        CompletableFuture.anyOf(a.result, b.result).get(5, TimeUnit.SECONDS);
        return a.result.isDone() ? a : b;
    }

    private static final class Worker<T> {
        private final CompletableFuture<T> result = new CompletableFuture<>();
        private final Thread thread;
        private volatile boolean interruptedOnReturn;

        Worker(Callable<T> op) {
            thread = daemon(() -> {
                try {
                    T value = op.call();
                    interruptedOnReturn = Thread.currentThread().isInterrupted();
                    result.complete(value);
                } catch (Throwable e) {
                    result.completeExceptionally(e);
                }
            });
            thread.start();
        }

        T get() throws Exception {
            return result.get(5, TimeUnit.SECONDS);
        }

        boolean interruptedOnReturn() {
            return interruptedOnReturn;
        }

        void interrupt() {
            thread.interrupt();
        }

        /**
         * Waits until the worker is parked in Condition.await() -- i.e. blocked on a full or
         * empty queue, not just briefly contending for the lock.
         */
        void awaitBlocked() throws InterruptedException {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (thread.getState() != Thread.State.WAITING
                    || !(LockSupport.getBlocker(thread) instanceof Condition)) {
                assertFalse(result.isDone(), "expected the call to block, but it returned");
                assertTrue(System.nanoTime() < deadline, "call never blocked; thread is " + thread.getState());
                Thread.sleep(1);
            }
        }
    }
}
