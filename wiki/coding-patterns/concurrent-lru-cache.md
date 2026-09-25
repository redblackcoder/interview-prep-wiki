# Concurrent LRU Cache (Single Lock → Sharded → Lock-Free Read)

A staple interview problem with a deep concurrency tail. The base LRU (`HashMap` +
doubly linked list) is easy; the real question is **how do you make it thread-safe and
then scale it?** The organizing insight: **in an exact LRU, `get()` reorders recency, so
`get` is a writer.** That single fact kills the obvious optimizations and drives every
design below. Ladder: single mutex → sharding → lock-free-read approximate (CLOCK).

## Base structure (write this first)

`HashMap<K, Node>` for O(1) lookup + a doubly linked list for O(1) recency ordering.
Use **sentinel head/tail nodes** so `addLast`/`remove` are two pointer writes with no
null checks. Keep the node's `key` on the node — eviction must delete the LRU node *and*
its map entry, and you can't do the latter without the key.

```java
static class Node { int key, val; Node prev, next; }   // key needed for eviction
// sentinels: head.next=tail, tail.prev=head
void remove(Node n)  { n.prev.next = n.next; n.next.prev = n.prev; }
void addLast(Node n) { n.prev = tail.prev; n.next = tail; tail.prev.next = n; tail.prev = n; }
```

`get`/`put` both `moveToLast` on a hit; `put` evicts `head.next` when full. Mention
`LinkedHashMap(accessOrder=true)` + `removeEldestEntry` as the ~10-line library escape hatch,
then build it manually since that's what's being tested.

## Approach 1 — single mutex, EXACT LRU

One `ReentrantLock` around the whole `get`/`put` body. The two traps:

- **A `ReadWriteLock`/`StampedLock` buys nothing.** `get` calls `moveToLast` — a mutation —
  so it's not a reader. (Real bug seen: a `StampedLock` optimistic-read `get` only ran
  `moveToLast` on the *validation-failure* branch, so recency never updated on the happy path.)
- **Thread-safe parts don't compose.** Per-method locks *inside* the list can't protect the
  cache's actual invariant — map and list agreeing — which spans multiple list calls **and**
  a separate `HashMap`. So the **cache owns one lock** and the list is a plain, lock-free
  data structure. (JCiP §4.4: compound actions need client-side locking on one lock.)

Correct, simple, but serializes every op → does not scale (adding cores makes it *worse*).

## Approach 2 — sharding (the "make it scale" answer)

N independent single-lock caches, routed by `hash(key) & (n-1)`. Converts one hot lock into
N → ~N-way parallelism, trivially correct.

```java
private LRUCache shardFor(int key) {
    int h = key; h ^= (h >>> 16);          // MIX high bits down before masking
    return shards[h & (shards.length - 1)];
}
```

- **Shard count must be a power of two.** `x & (n-1) == x % n` only then; `&` is a
  single-cycle op (vs division) and is always non-negative (Java `%` keeps the dividend's
  sign → negative index / crash).
- **Mix before masking.** `& (n-1)` reads only the low `log2(n)` bits; aligned/sequential/
  even keys share those and collapse into one shard. `h ^= h>>>16` folds high-bit entropy
  down (same as `HashMap`). Caveat: `>>>16` is calibrated for full-width hashes — a no-op for
  tiny ints, so match the shift to where the entropy actually lives.
- **Per-shard capacity** via `ceilDiv`: `(cap + n - 1) / n` so total ≥ requested.
- Trade-off: recency and the capacity bound are **per-shard** (approximate global). Skewed
  keys mean effective capacity < `n × perShard` — inherent to sharding.

## Approach 3 — CLOCK / second-chance (lock-free read, the sweet spot)

Because exact LRU forces `get` to be a writer, the *only* way to a lock-free read is to go
**approximate**. CLOCK is the ~40-line version and is what OS page caches / DB buffer pools use.

```java
int get(int key) {                 // LOCK-FREE: CHM read + one volatile write
    Node n = map.get(key);
    if (n == null) return -1;
    n.referenced = true;           // set a bit; no structural mutation
    return n.val;
}
void put(int key, int val) {       // one lock; readers never block
    // ... update-in-place, or fill empty slot, or evict:
    while (slots[hand].referenced) { slots[hand].referenced = false; hand = (hand+1)%cap; }
    map.remove(slots[hand].key);   // first UNREFERENCED slot = victim
    slots[hand] = node; hand = (hand+1)%cap;   // new node lands behind hand → full lap of grace
}
```

- Reads set a `referenced` bit; a rotating **hand** clears bits (second chance) and evicts
  the first unreferenced slot. Terminates: a full sweep clears every bit within one lap.
- `filled` is a one-way counter (0→cap, never wraps); `hand` is the cyclic index.
- `val`/`referenced` are `volatile` — read lock-free in `get`, written under lock in `put`;
  volatile is for **visibility** (an `int` doesn't tear; `long`/`double` would).
- Compose with sharding to parallelize `put` too → lock-free reads + N-way writes.

## Approach 2b — background snapshot (interesting, but usually not worth it)

`ConcurrentHashMap` + lock-free `get` that stamps `System.nanoTime()`; a background thread
periodically sorts keys by access time into an eviction snapshot that `put` pops from. It
*works* but is a minefield — the bugs are the lesson:

- Sort an **immutable `(key,time)` snapshot**, never the live `volatile`, or `Collections.sort`
  throws "comparison method violates its general contract."
- `scheduleWithFixedDelay` **cancels the task forever on any uncaught exception** → wrap the
  builder in try/catch and put the release in `finally`, or evictors deadlock on a stale snapshot.
- Use a **`Phaser`** so an evictor that drains the snapshot *parks* instead of busy-spinning
  (capture the phase *before* the work to avoid a lost wakeup).
- Capture the `volatile lru` into a **local** for a stable view within one iteration.
- `System.nanoTime()` (monotonic), never `Instant.now()` (wall-clock, non-monotonic, allocates).
- Second-chance skip via `buildTime` + `index.remove(key, node)` **compare-and-remove**, which
  also closes the "evict a freshly re-put entry" race for free (you already fetched the node).

## Performance under load (1–10)

| Approach | Read-heavy | Write-heavy | Why |
|---|---|---|---|
| Single lock (exact) | 2 | 2 | `get` is a writer → no read parallelism; worse with more cores |
| Snapshot (approx) | 8 | 6 | lock-free get; CAS writes but periodic O(n log n) rebuild + park/livelock |
| CLOCK (approx) | 9 | 5 | cheapest lock-free get, zero background cost; put serializes + hand sweep |

Read-heavy: `Clock ≳ Snapshot ≫ SingleLock`. Write-heavy: `Snapshot ≳ Clock ≫ SingleLock`.
**Sharding multiplies any of them.** Practical answer: **CLOCK + sharding**.

## Common bugs

- Using `ReadWriteLock`/`StampedLock` for `get` — it mutates, so it's a writer; a read lock lets
  concurrent `get`s corrupt the list.
- Two lock layers (list lock + cache lock) — the list lock can't cover the map; it's dead weight.
- `removeFirst` returning a *copy* of the node instead of unlinking the real one (breaks identity /
  leaves it linked).
- Sharding on raw `key & (n-1)` without mixing → patterned keys cluster into one shard.
- `Instant.now()` on the hot path — wall-clock (can go backwards) + allocates.
- Not breaking the eviction loop when the snapshot is empty → infinite busy-spin.

## Interview angle

> "Base is a HashMap plus a doubly linked list with sentinel nodes — O(1) get/put, and I keep
> the key on the node so I can evict its map entry. For thread safety: one lock on the cache, not
> the list, because the map-and-list invariant spans both — and note a ReadWriteLock is useless
> here since `get` reorders recency, so `get` is a writer. To scale, shard into N single-lock
> caches by `hash(key) & (n-1)`, mixing high bits down first so patterned keys don't cluster. If
> I need lock-free reads I go approximate with CLOCK: reads just set a referenced bit, and a
> rotating hand evicts the first unreferenced slot — exactly what OS page caches do. Exact LRU
> and lock-free reads are fundamentally in tension; every scalable cache trades exactness."

## Connections
- [[theory/concurrency-constructs]] — the toolkit: ReentrantLock vs ReadWriteLock vs StampedLock, CAS, Phaser, volatile visibility, `ConcurrentHashMap`
- [[coding-patterns/keyed-serial-executor]] — same "one lock owns the invariant, go lock-free where you safely can" reasoning
- [[system-design-concepts/cache-eviction-policies]] — the policy side (exact vs approximate LRU, LFU, CLOCK, W-TinyLFU) this code implements
- [[theory/consistent-hashing]] — sharding/routing and the hash-distribution concern, taken to the cross-node layer
- [[system-design-concepts/hot-key-write-contention]] — a hot key concentrates on one shard's lock; the in-process face of hot-key contention

## Sources
- [[sources/code/concurrent-lru-cache]] — the four implementations and the design journey (get-is-a-writer, sharding hash, snapshot bugs, CLOCK)
- Runnable code in raw: [concurrent-lru-cache/](https://github.com/redblackcoder/interview-prep-raw/blob/master/code/concurrent-lru-cache/)
