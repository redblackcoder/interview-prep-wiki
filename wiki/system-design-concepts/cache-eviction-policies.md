# Cache Eviction Policies (Exact vs Approximate LRU, LFU, CLOCK, W-TinyLFU)

When a cache is full, *which entry do you drop?* The textbook answer is LRU, but almost no
production cache implements exact LRU — because **exact LRU makes every read a write** (a
`get` must reorder recency), which is expensive and doesn't even give the best hit rate. The
real design space is a trade between **hit rate**, **read/write concurrency**, and
**implementation cost**, and the winning move is almost always *approximate* recency/frequency.

## The policy ladder

| Policy | Idea | Where used | Note |
|---|---|---|---|
| **LRU (exact)** | evict least-recently-used | textbooks, `LinkedHashMap(accessOrder)` | `get` reorders → get is a writer; scan-polluted |
| **LRU (sampling)** | evict oldest of K random keys | **Redis** `allkeys-lru` (K=5) | no linked list at all — approximate for a fraction of the cost |
| **CLOCK / second-chance** | per-entry referenced bit + rotating hand | OS page caches, DB buffer pools | O(1), lock-free reads (just set a bit) |
| **LFU (aged)** | evict least-frequently-used, decayed | Redis `allkeys-lfu` (8-bit log counter) | resists one-off scans; needs aging or it ossifies |
| **Segmented LRU / 2Q / ARC** | probationary + protected regions | ARC in ZFS | a scan can't evict the hot set; ARC self-tunes |
| **W-TinyLFU** | frequency-sketch admission filter + SLRU | **Caffeine** (JVM standard) | state of the art; admits only if more frequent than the victim |

## Why approximate wins

- **Exact LRU's per-access reordering is both costly and suboptimal.** A single large scan
  (read everything once) evicts your whole hot set under LRU. Frequency-aware policies
  (LFU, TinyLFU) and second-chance (CLOCK) resist that.
- **Reads must not mutate shared structure.** This is the concurrency crux (see
  [[coding-patterns/concurrent-lru-cache]]): every scalable cache removes the write from the
  read path — CLOCK reduces it to one bit, Redis sampling skips ordering entirely, Caffeine
  buffers accesses in a lock-free ring and **replays them lazily** under the next lock.
- **Sampling replaces the data structure.** Redis keeps *no* recency list; it samples K random
  keys and evicts the oldest. Approximate LRU for near-zero bookkeeping — the biggest
  "production ≠ textbook" surprise.

## The three independent axes (don't conflate them)

A production cache is designed along three separate dimensions:

1. **Eviction policy** → hit rate (this page).
2. **Concurrency model** → throughput under contention: lock striping/sharding, read
   buffers + amortized replay (Caffeine), CLOCK's single-bit reads. See
   [[coding-patterns/concurrent-lru-cache]].
3. **Memory management** → density & latency, *orthogonal to concurrency*:
   - **Slab allocation** (Memcached): fixed size-classes carved from big pages → eliminates
     external fragmentation (freed chunk always fits the next same-class request); costs bounded
     internal fragmentation + "slab calcification" (fixed by a slab rebalancer). Also sets the
     lock-striping boundary (per-class freelists).
   - **Off-heap** (`DirectByteBuffer`/`mmap`): keep entries out of the GC's view so a 50 GB
     cache causes no GC pause. Composes *with* slabs — off-heap gives the raw arena, slabs are
     the fragmentation-free allocator inside it; entries addressed by offset, a compact index
     stays on-heap.
   - **Compression/encoding** (Redis listpack/intset, shared integers): pure density.

## Expiration is separate from eviction

TTL is not capacity eviction. Redis does **lazy** (expire on access) **+ active** (background
sampler probabilistically scans for expired keys) expiration; Caffeine uses hierarchical
timing wheels for `expireAfter`. A full design has both a size policy and a time policy.

## Distributed layer (multi-node)

- **Consistent / rendezvous hashing** to place keys on nodes so add/remove remaps only `1/N`
  (see [[theory/consistent-hashing]]). Memcached = client-side hashing (dumb servers); Redis
  Cluster = 16384 managed hash slots.
- **Stampede protection**: single-flight/request coalescing, probabilistic early expiration,
  negative caching — see [[system-design-concepts/cache-stampede]].
- **Write policy**: write-through / write-back / write-around / cache-aside, plus invalidation
  (the genuinely hard part).

## Interview angle

> "I wouldn't ship exact LRU — it makes `get` a writer and a single scan evicts the hot set.
> Redis actually samples K random keys and evicts the oldest; OS page caches use CLOCK — a
> referenced bit plus a rotating hand — so reads are lock-free. Caffeine uses W-TinyLFU: a
> frequency sketch admits a new key only if it's more popular than the entry it would evict,
> which beats LRU on skewed and scan-heavy workloads. Two through-lines: approximate beats
> exact, and reads must never mutate shared state. Separately, eviction, concurrency, and
> memory layout (slabs, off-heap) are three independent axes."

## Connections
- [[coding-patterns/concurrent-lru-cache]] — the implementation side: making LRU/CLOCK thread-safe, single-lock → sharded → lock-free read
- [[system-design-concepts/cache-stampede]] — what happens on a *miss* (thundering herd); the read-path sibling of eviction
- [[theory/consistent-hashing]] — how the distributed cache layer places keys across nodes
- [[tech/aws-elasticache-redis]] — Redis's `maxmemory-policy` (sampled LRU/LFU), lazy+active TTL, cluster hash slots in practice
- [[system-design-concepts/hot-key-write-contention]] — a hot cached key is the read-side twin of write-side hot-key contention

## Sources
- [[sources/code/concurrent-lru-cache]] — production-cache techniques surveyed alongside the four hands-on LRU implementations (CLOCK, sampling, W-TinyLFU, slabs vs off-heap)
