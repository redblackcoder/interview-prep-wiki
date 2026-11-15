# Hot-Key Write Contention

The failure mode where a huge write rate lands on a **single logical key** — one auction, one viral post's like-count, one flash-sale SKU's inventory — and every write must agree with the others on the outcome. It is the crux hidden inside many "just scale it" problems, and the trap is that the **aggregate** number looks easy while the **per-key** number is the real design.

## The one-sentence mental model

> **Aggregate throughput shards away for free (partition by key); contention on *one* key does not — because by definition every write to that key must be reconciled against the same state. So the question is never "can I handle N writes/s?", it's "can I handle N writes/s *to one key*?"**

## Why the aggregate number is a decoy

Take the auction sizing: ~100k bids/s across ~700k live auctions is a fraction of a bid per auction per second — trivially shardable, laptop-scale. But bids are **heavily skewed**: the last ten seconds of one influencer's auction might see **10k+ bids/s against that single `auction_id`**. Partitioning by `auction_id` spreads the *aggregate* perfectly and does **nothing** for the hot key — all 10k/s still collide on one partition. Same shape as a celebrity tweet's like counter or a Black-Friday inventory row.

> Diagnostic reflex: whenever a peak number appears, ask **"is this load spread across keys, or concentrated on one?"** If concentrated, the aggregate QPS is irrelevant and you must design the single-key path.

## The three ways out (in preference order)

1. **Remove the coordination** — if the update is a **commutative/associative/idempotent aggregate** (`max`, `sum`, set-union, counter), the result is order-invariant, so you don't serialize writes at all: **append to a per-key log and defer the aggregation** (batched, layered, even split across sub-partitions since the aggregate recombines). This is the auction answer and the strongest move. See [commutative aggregation](commutative-aggregation.md).
2. **Concentrate the coordination and make it cheap** — if you *must* enforce an order-sensitive invariant (uniqueness, "don't oversell", strict balance), route the key through a **single writer / per-shard leader** and **batch** many pending updates into one state transition (group commit). One owner, high-throughput batched apply — not a distributed lock per write.
3. **Approximate** — if exactness isn't required, shard the counter into N per-node sub-counters and sum lazily (the "consistent vs. accurate" split in [global rate limiting](global-rate-limiting.md)); accept bounded staleness to kill the contention.

The anti-patterns: a **row lock + read-modify-write** on the hot row (serializes everyone, MVCC bloat, latency blows past budget), or a **distributed lock per write** (a coordination round-trip on the hottest path in the system).

## Key points
- **You cannot partition away single-key contention** — partitioning solves aggregate load, which is the *easy* half. Splitting a hot key across sub-partitions only works when the per-partition results **recombine** (i.e. the op is an aggregate).
- **Batching is the universal lever** on the contended path — amortize the expensive step (log append, leader apply, fsync) over many writes; it converts per-write cost into per-batch cost.
- **Write-time vs. decision-time.** With the log approach the synchronous write is just a durable **append** ("accepted"); the authoritative value is computed downstream/at a cutoff — so acknowledgment ≠ adjudication (name the async return path).
- The choice among the three exits is dictated by the **operation's algebra**: order-free aggregate → remove coordination; order-sensitive invariant → concentrate it; tolerant of error → approximate.
- **Hot tenant is the same shape at coarser grain.** In a tenant-sharded multi-tenant platform, one tenant at 30% of a shard is a hot key. Don't change the shard key globally (job-ID sharding loses tenant-scoped reads, cross-shard tenant ops, and isolation); **split only the hot tenants** into `tenant#bucket` sub-shards via a directory. And if what's contended is *execution capacity* rather than storage, the fix is per-tenant fair share at dispatch, not sharding — see [fair share scheduling](fair-share-scheduling.md).
- **The read-side mirror is a hot *read* key.** A viral object creates hot keys at multiple layers: its **view/like counter is a hot write key** (this page — shard/approximate it), while its **metadata is a hot *read* key** (one row read by millions on every feed hydration). Reads don't contend, so the read-side fix is *replication/caching*, not a log: edge/replica-cache the metadata, and serve the bytes from a [cdn](../tech/cdn.md). Naming both when an object goes viral is the complete answer; "the CDN handles it" covers only the bytes.

## Interview angle

> "The first thing I do with a hot-write feature is separate aggregate load from single-key contention. 100k bids a second across all auctions is nothing — I shard by auction_id. The real problem is 10k bids a second on *one* auction in the final seconds, and partitioning does nothing for that because every bid contends on the same state. Then I look at the operation: if it's an order-free aggregate like `max`, I don't serialize at all — I append every bid to a per-auction log and compute the running max downstream, which even lets me split the hot key across sub-partitions since maxes recombine. If instead I had an order-sensitive invariant like 'don't oversell inventory,' I'd funnel the key through a single leader and batch-apply. Locking the hot row is the thing I *don't* do."

## Connections
- [commutative aggregation](commutative-aggregation.md) — the preferred exit: order-free ops need no coordination, so contention dissolves into an append + deferred fold
- [the log abstraction](the-log-abstraction.md) — the concrete mechanism for exit #1: per-key append-only log + materialized aggregate
- [hash vs range partitioning](hash-vs-range-partitioning.md) — partitioning is what makes *aggregate* load easy and is exactly what fails on a hot key
- [global rate limiting](global-rate-limiting.md) — exit #3 in the wild: sharded "accurate" counters vs. a single "consistent" one
- [consistency models](../theory/consistency-models.md) — whether you *need* coordination is a consistency question; PACELC's "else latency-vs-consistency" is the everyday axis here
- [event time vs processing time](event-time-vs-processing-time.md) — the deferred aggregate is usually a *windowed* fold, inheriting watermark/cutoff concerns
- [geospatial indexing](geospatial-indexing.md) — the **spatial twin**: a hot *cell* (stadium/airport/surge) is a hot key in 2-D; aggregate location load shards, cell concentration doesn't
- [timeline fanout hybrid](timeline-fanout-hybrid.md) — the **fan-out cousin**: a celebrity post is O(followers) writes on the hot path; the fix (don't push, pull-and-stitch) is the same "don't do the hot work" instinct
- [read side fanout](read-side-fanout.md) — the read-side mirror when the hot read is a *live* value pushed to many (vs. static bytes on a CDN)
- [cdn](../tech/cdn.md) — where the hot-read *bytes* go; the counterpart to sharding the hot write
- [fair share scheduling](fair-share-scheduling.md) — the **tenant-granularity** version: hot tenant in a multi-tenant scheduler; split hot tenants, ration worker capacity

## Sources
- [live auctions](../problems/system-design/live-auctions.md) — the auction bid path; naming single-key contention as the crux, and `max`-log as the exit
- [ride dispatch](../problems/system-design/ride-dispatch.md) — the hot-cell analog
- [short video feed](../problems/system-design/short-video-feed.md) — the viral video: view-counter hot write key + metadata hot read key, both outside the CDN
- [distributed job scheduler](../problems/system-design/distributed-job-scheduler.md) — hot tenant: split hot tenants + fair share, not just a shard-key change
