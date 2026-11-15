# Concurrent LRU Cache

Four progressively more scalable takes on a thread-safe LRU cache, plus the design
reasoning behind each. The interesting axis is **concurrency on the read path**: an
exact LRU makes `get()` a writer (it reorders recency), so the scaling story is about
how much exactness you trade to make reads lock-free.

## Implementations

| File | Approach | LRU accuracy | get path | put path |
|------|----------|--------------|----------|----------|
| `DLLSimple.java` | Doubly linked list w/ sentinels (shared building block) | — | — | — |
| `LRUCache.java` | Single `ReentrantLock`, exact LRU | Exact | locked | locked |
| `ShardedLRUCache.java` | N single-lock shards, `hash(key) & (n-1)` | Exact per-shard | locked (1 of N) | locked (1 of N) |
| `FasterLRUCache.java` | Background snapshot + lock-free get, approximate | Approximate | **lock-free** | CAS + park |
| `ClockCache.java` | CLOCK / second-chance, lock-free get | Approximate (NRU) | **lock-free** | locked (ring hand) |

## Key design points

- **Sentinels** in `DLLSimple` erase every null check in `addLast`/`remove`.
- **`get` is a writer** in exact LRU → a `ReadWriteLock`/`StampedLock` buys nothing; use one plain mutex.
- **Thread-safe parts don't compose**: per-method locking on the list can't protect the
  map+list invariant, which spans multiple calls and a separate `HashMap`. One lock in the cache.
- **Sharding**: shard COUNT must be a power of two (for the `&` mask); mix high bits into
  low bits before masking or aligned/sequential keys cluster into one shard. Per-shard
  capacity via `ceilDiv` so total >= requested.
- **Snapshot cache** (`FasterLRUCache`) landmines, all fixed in the code:
  - sort an immutable `(key,time)` snapshot, never the live `volatile` (else
    "Comparison method violates its general contract");
  - `try/catch` the builder + `arrive()` in `finally` (`scheduleWithFixedDelay` cancels
    the task forever on any uncaught exception → deadlocked evictors);
  - `Phaser` parks evictors when the snapshot is drained instead of busy-spinning;
  - capture the `volatile lru` into a local for a stable view;
  - `System.nanoTime()` (monotonic), never `Instant.now()` (wall-clock, non-monotonic, allocates);
  - second-chance skip via `buildTime` + `remove(key, node)` compare-and-remove.
- **CLOCK**: reads set a `referenced` bit; a rotating hand clears bits (second chance)
  and evicts the first unreferenced slot. New node lands behind the hand → a full lap of
  grace. `filled` is a one-way counter (never wraps); `hand` is the cyclic one.
- **`volatile val`**: read lock-free in `get`, written under lock in `put` — volatile is
  for visibility (int doesn't tear; `long`/`double` would).

## Performance under load (read-heavy vs write-heavy, 1–10)

| Impl | Read-heavy | Write-heavy | Note |
|------|-----------|-------------|------|
| `LRUCache` | 2 | 2 | one lock, get mutates → no read parallelism; worse with more cores |
| `FasterLRUCache` | 8 | 6 | lock-free get; CAS-based writes but periodic O(n log n) rebuild + park/livelock |
| `ClockCache` | 9 | 5 | cheapest lock-free get (bit vs timestamp), no background cost; put serializes + hand sweep |

Read-heavy: `Clock ≳ FasterLRU ≫ LRUCache`. Write-heavy: `FasterLRU ≳ Clock ≫ LRUCache`.
**Sharding multiplies any of them** — the practical production answer is *Clock + sharding*:
lock-free reads and N-way write parallelism with far less machinery than the snapshot approach.

## Run
```
javac *.java
```
JDK 8+. These are library classes (no `main`); drive them from a JUnit suite. `FasterLRUCache`
starts a background scheduler thread — call `stop()` to shut it down.
