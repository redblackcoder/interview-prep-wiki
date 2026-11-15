package practice.prep.concurrent.lrucache;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.Phaser;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

// Approach 2: snapshot-based APPROXIMATE LRU with a LOCK-FREE get path.
//
// - get(): ConcurrentHashMap read + one volatile timestamp write. No lock, no
//   structural mutation on the read path.
// - A background thread periodically materializes an eviction-ordered snapshot
//   (keys sorted by last-access time). put() evicts by popping the cold end of
//   that snapshot.
//
// Key correctness lessons baked in (see README for the full journey):
//  * Sort an IMMUTABLE (key,time) snapshot, never the live volatile → avoids
//    "Comparison method violates its general contract".
//  * Builder wrapped in try/catch and arrive() in finally → scheduleWithFixedDelay
//    cancels the task on any uncaught exception, which would deadlock evictors.
//  * Phaser turns the "snapshot drained but still must evict" spin into a park.
//  * Capture `lru` into a local once (it is volatile and swapped by the builder).
//  * Second-chance skip: if a popped key was accessed since the snapshot was built
//    (lastAccessTime >= buildTime), skip it instead of evicting a hot entry.
//  * remove(key, node) (compare-and-remove) closes the re-put race for free — we
//    already fetched the node for the timestamp check.
//
// Residual: approximate ordering; a get() in the tiny window between the timestamp
// check and remove still slips through (inherent TOCTOU on an in-place mutable node).
public class FasterLRUCache {
    static class Node {
        volatile int val;
        volatile long lastAccessTime;

        Node(int val, long lastAccT) {
            this.val = val;
            lastAccessTime = lastAccT;
        }
    }

    static class LRU {
        private final List<Integer> keys;
        private final AtomicInteger size;
        final long buildTime;

        LRU(List<Integer> keys, long buildTime) {
            this.keys = keys;
            this.size = new AtomicInteger(keys.size());
            this.buildTime = buildTime;
        }

        // Atomically hand out indices from the tail toward the head. -1 when drained.
        int removeLast() {
            int index = claimLastIndex();
            return index == -1 ? -1 : keys.get(index);
        }

        private int claimLastIndex() {
            while (true) {
                int curr = size.get();
                if (curr == 0) return -1;
                if (size.compareAndSet(curr, curr - 1)) return curr - 1;
            }
        }
    }

    private static final double EVICT_FRAC = 0.95;
    private static final double LRU_BUILD_FRAC = 0.75;
    private static final int LRU_BUILD_INTERVAL_MSEC = 200;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final Phaser lruPhaser = new Phaser(1);
    private final int cap;
    private final Map<Integer, Node> index;
    private volatile LRU lru;

    public FasterLRUCache(int cap) {
        this.cap = cap;
        index = new ConcurrentHashMap<>();
        lru = new LRU(new ArrayList<>(), nowNanos());
        scheduler.scheduleWithFixedDelay(this::buildLru,
                LRU_BUILD_INTERVAL_MSEC, LRU_BUILD_INTERVAL_MSEC, TimeUnit.MILLISECONDS);
    }

    public int get(int key) {                         // LOCK-FREE
        Node n = index.get(key);
        if (n == null) return -1;
        n.lastAccessTime = nowNanos();
        return n.val;
    }

    public void put(int key, int value) {
        Node n = index.get(key);
        if (n != null) {                              // update in place
            n.val = value;
            n.lastAccessTime = nowNanos();
            return;
        }

        while (mustEvict()) {
            LRU snap = lru;                           // one volatile read → stable view
            int curr = lruPhaser.getPhase();          // capture BEFORE work (no lost wakeup)
            int removed = snap.removeLast();
            if (removed == -1) {
                lruPhaser.awaitAdvance(curr);         // snapshot drained → wait for a rebuild
                continue;
            }
            Node victim = index.get(removed);
            if (victim != null && victim.lastAccessTime < snap.buildTime) {
                index.remove(removed, victim);        // compare-and-remove: skip if re-put
            }
            // else: accessed since snapshot (second chance) or already gone → try next
        }

        index.put(key, new Node(value, nowNanos()));
    }

    public void stop() {
        scheduler.shutdown();
    }

    private long nowNanos() {
        return System.nanoTime();                     // monotonic, cheap, no allocation
    }

    private boolean mustEvict() {
        return index.size() >= (int) Math.floor(EVICT_FRAC * cap);
    }

    private boolean mustBuildLru() {
        return index.size() >= (int) Math.floor(LRU_BUILD_FRAC * cap);
    }

    private void buildLru() {
        try {
            if (!mustBuildLru()) return;

            long buildTime = nowNanos();
            List<Integer> tempLru = new ArrayList<>();
            Map<Integer, Long> keyTime = new HashMap<>();  // immutable (key,time) snapshot
            index.forEach((k, v) -> {
                tempLru.add(k);
                keyTime.put(k, v.lastAccessTime);
            });

            // sort descending by access time → cold keys at the tail (evicted first)
            Collections.sort(tempLru, (k1, k2) -> Long.compare(keyTime.get(k2), keyTime.get(k1)));

            lru = new LRU(tempLru, buildTime);
        } catch (Exception exp) {
            System.out.println("Error while building LRU: " + exp.getMessage());
        } finally {
            lruPhaser.arrive();                        // always release parked evictors
        }
    }
}
