package com.practice.problems;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class HitCounterTest {

    @Test
    public void testBasicHitsWithinWindow() {
        HitCounter counter = new HitCounter(300);
        counter.hit(1);
        counter.hit(2);
        counter.hit(3);

        // All hits are within the past 300 seconds
        assertEquals(3, counter.getHits(4));
    }

    @Test
    public void testMultipleHitsAtSameTimestamp() {
        HitCounter counter = new HitCounter(300);
        counter.hit(10);
        counter.hit(10);
        counter.hit(10);
        counter.hit(20);

        assertEquals(4, counter.getHits(20));
    }

    @Test
    public void testHitsEvictedOutsideWindow() {
        HitCounter counter = new HitCounter(300);
        counter.hit(1);
        counter.hit(2);
        counter.hit(300);

        // At timestamp 300, hits at 1 and 2 are still valid (window is 1 to 300)
        assertEquals(3, counter.getHits(300));

        // At timestamp 301, hit at 1 should be evicted (window is 2 to 301)
        // Note: Based on standard HitCounter requirements, this should be 2.
        // (If the underlying code has an off-by-one boundary bug, this test will catch
        // it).
        assertEquals(2, counter.getHits(301));

        // At timestamp 302, hit at 2 should also be evicted
        assertEquals(1, counter.getHits(302));
    }

    @Test
    public void testLargeGapBetweenHits() {
        HitCounter counter = new HitCounter(300);
        counter.hit(100);

        // Fast forward way past the window
        assertEquals(0, counter.getHits(600));

        // Add a new hit and ensure only the new one is counted
        counter.hit(605);
        assertEquals(1, counter.getHits(605));
    }

    @Test
    public void testConcurrentHitsThreadSafety() throws InterruptedException {
        HitCounter counter = new HitCounter(300);
        int numThreads = 100;
        int hitsPerThread = 1000;

        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(numThreads);

        for (int i = 0; i < numThreads; i++) {
            executor.submit(() -> {
                try {
                    // Force all threads to wait here until the latch is released
                    startLatch.await();

                    for (int j = 0; j < hitsPerThread; j++) {
                        // Hammer the exact same timestamp to test internal locks
                        counter.hit(150);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown(); // Report this thread is finished
                }
            });
        }

        // Release the latch, unleashing all 100 threads at the exact same time
        startLatch.countDown();

        // Wait up to 5 seconds for all threads to finish their work
        doneLatch.await(5, java.util.concurrent.TimeUnit.SECONDS);
        executor.shutdown();

        // 100 threads * 1000 hits = 100,000 expected hits
        assertEquals(100000, counter.getHits(150));
    }

    @Test
    public void testConcurrentTimeProgressionAndCleanup() throws Exception {
        HitCounter counter = new HitCounter(300);
        int numThreads = 20;
        int maxTime = 600; // Simulate 10 minutes passing

        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        // The barrier ensures all 20 threads finish timestamp T before anyone moves to
        // T+1
        CyclicBarrier timeTickBarrier = new CyclicBarrier(numThreads);

        List<Callable<Void>> tasks = new java.util.ArrayList<>();

        for (int i = 0; i < numThreads; i++) {
            final int threadId = i;
            tasks.add(() -> {
                for (int ts = 1; ts <= maxTime; ts++) {
                    // Wait for all threads to arrive at this specific second in time
                    timeTickBarrier.await();

                    // Half the threads record hits, the other half trigger reads (and cleanup)
                    if (threadId % 2 == 0) {
                        counter.hit(ts);
                    } else {
                        counter.getHits(ts);
                    }
                }
                return null;
            });
        }

        // Execute all tasks and wait for them to complete
        for (java.util.concurrent.Future<Void> future : executor.invokeAll(tasks)) {
            future.get(); // Throws exception if any thread failed
        }
        executor.shutdown();

        // VALIDATION MATH:
        // Threads: 10 threads were hitting, 10 were reading. So 10 hits per timestamp.
        // Eviction logic in code: if (k <= ts - window) hits.remove(k)
        // At ts = 600, window = 300. Keys <= 300 are removed.
        // Remaining valid keys: 301 through 600 (inclusive).
        // That is 300 distinct timestamps * 10 hits per timestamp = 3000 hits.

        assertEquals(3000, counter.getHits(600));
    }
}
