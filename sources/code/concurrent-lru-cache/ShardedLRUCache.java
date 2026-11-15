package practice.prep.concurrent.lrucache;

// Approach 1b: N single-lock LRUCache shards keyed by hash(key) & (n-1).
// Converts one hot lock into N → ~N-way write parallelism, trivial to reason about.
// Trade-off: recency and capacity are per-shard (approximate global bound).
// Shard COUNT must be a power of two (for the & mask); per-shard capacity uses
// ceilDiv so total >= requested cap.
public class ShardedLRUCache {
    private final LRUCache[] shards;

    public ShardedLRUCache(int n, int cap) {
        if (Integer.bitCount(n) != 1) {
            throw new IllegalArgumentException("Number of shards must be a power of two, supplied: " + n);
        }
        shards = new LRUCache[n];
        int perShard = (cap + n - 1) / n;           // ceilDiv: never under-provision
        for (int i = 0; i < n; i++) {
            shards[i] = new LRUCache(perShard);
        }
    }

    public int get(int key) {
        return shardFor(key).get(key);
    }

    public void put(int key, int val) {
        shardFor(key).put(key, val);
    }

    private LRUCache shardFor(int key) {
        // Mix high bits into low bits before masking — raw int keys (aligned,
        // sequential, all-even) have poor low-bit entropy and would cluster.
        int h = key;
        h ^= (h >>> 16);
        return shards[h & (shards.length - 1)];     // & is fast modulo + always non-negative
    }
}
