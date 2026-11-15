package practice.prep.concurrent.lrucache;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

// Approach 3: CLOCK / second-chance. The interview sweet spot — LOCK-FREE get,
// simple locked put, ~40 lines, and it is what OS page caches / DB buffer pools use.
//
// Reads never reorder anything: get() sets a referenced bit. Eviction is a rotating
// "hand" over a fixed ring of slots; a referenced slot gets a second chance (bit
// cleared, hand advances), the first unreferenced slot is the victim.
//
// Approximate LRU (not-recently-used), not exact — the accepted cost of a lock-free read.
// Compose with sharding to also parallelize put().
public class ClockCache {
    static final class Node {
        final int key;
        volatile int val;              // volatile: read lock-free in get(), written under lock in put()
        volatile boolean referenced;

        Node(int key, int val) {
            this.key = key;
            this.val = val;
        }
    }

    private final int cap;
    private final Node[] slots;        // the clock face
    private final Map<Integer, Node> map = new ConcurrentHashMap<>();
    private final ReentrantLock lock = new ReentrantLock();
    private int hand = 0;
    private int filled = 0;            // one-way fill counter: 0..cap, then stops (never wraps)

    public ClockCache(int cap) {
        this.cap = cap;
        this.slots = new Node[cap];
    }

    public int get(int key) {          // LOCK-FREE: CHM read + one volatile write
        Node n = map.get(key);
        if (n == null) return -1;
        n.referenced = true;           // no structural mutation → nothing to lock
        return n.val;
    }

    public void put(int key, int val) {
        lock.lock();
        try {
            Node existing = map.get(key);
            if (existing != null) {            // update in place
                existing.val = val;
                existing.referenced = true;
                return;
            }
            Node node = new Node(key, val);
            if (filled < cap) {                // fill an empty slot during warm-up
                slots[filled++] = node;
            } else {                            // evict via the clock hand
                while (slots[hand].referenced) {
                    slots[hand].referenced = false;      // second chance
                    hand = (hand + 1) % cap;
                }
                map.remove(slots[hand].key);             // first unreferenced = victim
                slots[hand] = node;
                hand = (hand + 1) % cap;                 // new node lands BEHIND the hand → full lap of grace
            }
            map.put(key, node);
        } finally {
            lock.unlock();
        }
    }
}
