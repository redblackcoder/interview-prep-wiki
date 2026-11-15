package practice.prep.concurrent.lrucache;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

// Approach 1: single mutex, EXACT LRU.
// get() mutates recency (moveToLast) → get is a writer, so a ReadWriteLock
// buys nothing. One lock guards the whole map+list compound action.
// Simple and correct; serializes every op, so it does not scale under load.
public class LRUCache {
    private final int cap;
    private final DLLSimple dll;
    private final Map<Integer, DLLSimple.Node> index;
    private final Lock lock = new ReentrantLock();

    public LRUCache(int cap) {
        this.cap = cap;
        dll = new DLLSimple();
        index = new HashMap<>();
    }

    public int get(int key) {
        lock.lock();
        try {
            DLLSimple.Node node = index.get(key);
            if (node == null) {
                return -1;
            }
            dll.moveToLast(node);
            return node.val;
        } finally {
            lock.unlock();
        }
    }

    public void put(int key, int val) {
        lock.lock();
        try {
            DLLSimple.Node node = index.get(key);
            if (node != null) {                 // hit: update + refresh, done
                node.val = val;
                dll.moveToLast(node);
                return;
            }

            if (index.size() == cap) {          // full: evict LRU (front)
                DLLSimple.Node lru = dll.removeFirst();
                index.remove(lru.key);
            }

            node = new DLLSimple.Node(key, val); // insert
            dll.addLast(node);
            index.put(key, node);
        } finally {
            lock.unlock();
        }
    }
}
