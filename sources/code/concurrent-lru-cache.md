---
source: code/concurrent-lru-cache/
source_url: https://github.com/redblackcoder/interview-prep-raw/blob/master/code/concurrent-lru-cache/
type: code
date_extracted: 2026-09-24
topic: Interview Prep Wiki
---

# Concurrent LRU Cache — four approaches

Built an LRU cache four ways, from an interview-simple single-lock version up to a
lock-free-read approximate cache, tracing the concurrency reasoning at each step. The
throughline: **in an exact LRU, `get()` reorders recency, so `get` is a writer** — that
single fact drives every scaling decision.

## Key Ideas
- **Sentinel head/tail nodes** collapse a doubly linked list's `addLast`/`remove` to two
  pointer assignments with no null checks — the single biggest interview-speed win.
- **`get` is a writer** in exact LRU → a `ReadWriteLock`/`StampedLock` gives nothing; use
  one plain mutex. (Discovered by debugging a `StampedLock` version whose optimistic-read
  fast path skipped the `moveToLast`, so recency never updated — a failing test.)
- **Thread-safe components don't compose.** Per-method locks inside the linked list can't
  protect the cache's real invariant (map and list agreeing), which spans multiple list
  calls *and* a separate `HashMap`. The cache must own one lock; the list becomes a plain,
  lock-free data structure.
- **Sharding** = N single-lock caches keyed by `hash(key) & (n-1)`. Shard count must be a
  power of two (mask trick, also avoids negative `%`); **mix high bits into low bits before
  masking** or aligned/sequential keys cluster into one shard; per-shard capacity via
  `ceilDiv` so total ≥ requested.
- **Lock-free reads require approximate LRU.** Two ways: (a) background snapshot that
  materializes eviction order periodically; (b) CLOCK second-chance bit. Both make `get`
  a `ConcurrentHashMap` read + a cheap volatile write, never a structural mutation.
- **CLOCK is the interview sweet spot**: lock-free `get`, simple locked `put`, ~40 lines,
  and it's what OS page caches / DB buffer pools actually use.

## My Understanding
I now think about a concurrent LRU as a tension between *exactness* and *read concurrency*,
and I can't have both. The naive version locks `get` and `put` on one mutex; it's correct
and I'd write it first in an interview, but it doesn't scale because `get` mutates recency,
so every read serializes — a read-write lock is a trap here.

Sharding is my go-to "make it scale" answer: cheap, obviously correct, N-way parallelism.
The subtlety I learned is the routing hash — `key & (n-1)` only looks at the low bits, so
patterned keys (multiples of 4, all even) collapse into one shard; I fold high bits down
with `h ^= h >>> 16` before masking, same as `HashMap`. And that `>>> 16` is calibrated for
full-width hashes, so it's a no-op for tiny ints — the general point is "match the shift to
where the entropy is."

For genuinely lock-free reads I built two approximate caches. The snapshot version taught me
the most through its bugs: a periodic background thread sorts keys by access time into a
snapshot, and `put` evicts from the cold end. Getting it right meant (1) sorting an immutable
`(key,time)` copy, not the live volatile — otherwise `Collections.sort` throws "comparison
method violates its general contract"; (2) wrapping the builder in try/catch and putting
`phaser.arrive()` in `finally`, because `scheduleWithFixedDelay` **cancels the task forever**
on any uncaught exception, which then deadlocks evictors on a stale snapshot; (3) a `Phaser`
so an evictor that drains the snapshot *parks* instead of busy-spinning; (4) capturing the
`volatile lru` into a local for a stable view within one iteration; (5) second-chance skip
via `buildTime` plus `remove(key, node)` compare-and-remove, which also closes the "evict a
freshly re-put entry" race for free since I already fetched the node for the timestamp check.

CLOCK is where I landed as the best value. Reads set a `referenced` bit; a rotating hand
clears bits (second chance) and evicts the first unreferenced slot. A new node lands *behind*
the hand so it gets a full lap of grace. It's approximate (not-recently-used, not strictly
LRU) but that's the same trade every production cache makes — and it beats the snapshot
version on the common read-heavy workload with none of the background-thread machinery.

30-second interview version: "Single lock is correct but `get` is a writer so it won't scale.
Shard it for N-way parallelism. If you need lock-free reads, go approximate — CLOCK: reads
set a bit, a rotating hand evicts the first unreferenced slot. That's what OS page caches do."

## Open Questions
- Caffeine's read-buffer + amortized replay gives lock-free reads with *near-exact* LRU —
  how does the ring-buffer replay actually reconcile order without a lock? Worth a deeper read.
- The snapshot cache's `remove(key, node)` only guards node *replacement*, not in-place
  `lastAccessTime` mutation between check and remove. Fully closing that needs a CAS "mark
  dead only if still cold" on the node — is that ever worth it, or is the sliver always fine?
- Under sustained write bursts the snapshot cache can livelock (snapshot drains before the
  200ms rebuild). Is there a bound worth enforcing, or does sharding make it moot?

## Connections
- Relates to: [[wiki/system-design-concepts/cache-eviction-policies]] — LRU vs LFU vs CLOCK vs W-TinyLFU, exact vs approximate, and the slabs/off-heap memory axis.
- Relates to: [[wiki/coding-patterns/concurrent-lru-cache]] — the implementation-pattern page this seeds.
- Relates to: [[wiki/theory/concurrency-constructs]] — ReentrantLock vs ReadWriteLock vs StampedLock, Phaser, CAS, volatile visibility.
- Builds on: [[sources/docs/azure-storage-interview-loop]] — same "one lock owns the invariant / lock-free where you can" reasoning as KeyedTaskExecutor.
- Relates to: [[wiki/theory/consistent-hashing]] — sharding/routing and the hash-distribution concern reappear at the node level.

## Key Quotes / Annotations
- "Exact LRU forces `get` to be a writer; every technique here buys read concurrency by trading away exact global recency."
- "Thread-safe building blocks don't make a thread-safe aggregate" — the compound map+list action needs one lock (JCiP §4.4).
- Performance under load (1–10): read-heavy `Clock 9 ≳ FasterLRU 8 ≫ LRUCache 2`; write-heavy `FasterLRU 6 ≳ Clock 5 ≫ LRUCache 2`. Sharding multiplies any of them.
