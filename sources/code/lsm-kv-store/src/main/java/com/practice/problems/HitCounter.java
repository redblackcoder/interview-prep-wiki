package com.practice.problems;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Design a hit counter which counts the number of hits received in the past 5
 * minutes (i.e., the past 300 seconds).
 * 
 * Your system should accept a timestamp parameter (in seconds granularity), and
 * you may assume that calls are being made to the system in chronological order
 * (i.e., timestamp is monotonically increasing). Several hits may arrive
 * roughly at the same time.
 * 
 * Implement the HitCounter class:
 * 
 * HitCounter() Initializes the object of the hit counter system.
 * void hit(int timestamp) Records a hit that happened at timestamp (in
 * seconds). Several hits may happen at the same timestamp.
 * int getHits(int timestamp) Returns the number of hits in the past 5 minutes
 * from timestamp (i.e., the past 300 seconds).
 */
public class HitCounter {
    private int window;
    private Map<Integer, AtomicInteger> hits;

    public HitCounter(int window) {
        this.window = window;
        hits = new ConcurrentHashMap<>();
    }

    public void hit(int ts) {
        hits.computeIfAbsent(ts, k -> new AtomicInteger(0)).incrementAndGet();
    }

    public int getHits(int ts) {
        Set<Integer> keys = new HashSet<>(hits.keySet());
        int total = 0;
        for (int k : keys) {
            if (k <= ts - window) {
                hits.remove(k);
            } else if (k <= ts) {
                total += hits.get(k).get();
            }
        }

        return total;
    }
}
