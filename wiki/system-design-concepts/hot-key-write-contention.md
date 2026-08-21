# Hot-Key Write Contention

The failure mode where a huge write rate lands on a **single logical key** — one auction, one viral post's like-count, one flash-sale SKU's inventory — and every write must agree with the others on the outcome. It is the crux hidden inside many "just scale it" problems, and the trap is that the **aggregate** number looks easy while the **per-key** number is the real design.

## The one-sentence mental model

> **Aggregate throughput shards away for free (partition by key); contention on *one* key does not — because by definition every write to that key must be reconciled against the same state. So the question is never "can I handle N writes/s?", it's "can I handle N writes/s *to one key*?"**

## Why the aggregate number is a decoy

Take the auction sizing: ~100k bids/s across ~700k live auctions is a fraction of a bid per auction per second — trivially shardable, laptop-scale. But bids are **heavily skewed**: the last ten seconds of one influencer's auction might see **10k+ bids/s against that single `auction_id`**. Partitioning by `auction_id` spreads the *aggregate* perfectly and does **nothing** for the hot key — all 10k/s still collide on one partition. Same shape as a celebrity tweet's like counter or a Black-Friday inventory row.

> Diagnostic reflex: whenever a peak number appears, ask **"is this load spread across keys, or concentrated on one?"** If concentrated, the aggregate QPS is irrelevant and you must design the single-key path.

## The three ways out (in preference order)

1. **Remove the coordination** — if the update is a **commutative/associative/idempotent aggregate** (`max`, `sum`, set-union, counter), the result is order-invariant, so you don't serialize writes at all: **append to a per-key log and defer the aggregation** (batched, layered, even split across sub-partitions since the aggregate recombines). This is the auction answer and the strongest move. See [[system-design-concepts/commutative-aggregation]].
2. **Concentrate the coordination and make it cheap** — if you *must* enforce an order-sensitive invariant (uniqueness, "don't oversell", strict balance), route the key through a **single writer / per-shard leader** and **batch** many pending updates into one state transition (group commit). One owner, high-throughput batched apply — not a distributed lock per write.
3. **Approximate** — if exactness isn't required, shard the counter into N per-node sub-counters and sum lazily (the "consistent vs. accurate" split in [[system-design-concepts/global-rate-limiting]]); accept bounded staleness to kill the contention.

The anti-patterns: a **row lock + read-modify-write** on the hot row (serializes everyone, MVCC bloat, latency blows past budget), or a **distributed lock per write** (a coordination round-trip on the hottest path in the system).

## Key points
- **You cannot partition away single-key contention** — partitioning solves aggregate load, which is the *easy* half. Splitting a hot key across sub-partitions only works when the per-partition results **recombine** (i.e. the op is an aggregate).
- **Batching is the universal lever** on the contended path — amortize the expensive step (log append, leader apply, fsync) over many writes; it converts per-write cost into per-batch cost.
- **Write-time vs. decision-time.** With the log approach the synchronous write is just a durable **append** ("accepted"); the authoritative value is computed downstream/at a cutoff — so acknowledgment ≠ adjudication (name the async return path).
- The choice among the three exits is dictated by the **operation's algebra**: order-free aggregate → remove coordination; order-sensitive invariant → concentrate it; tolerant of error → approximate.

## Interview angle

> "The first thing I do with a hot-write feature is separate aggregate load from single-key contention. 100k bids a second across all auctions is nothing — I shard by auction_id. The real problem is 10k bids a second on *one* auction in the final seconds, and partitioning does nothing for that because every bid contends on the same state. Then I look at the operation: if it's an order-free aggregate like `max`, I don't serialize at all — I append every bid to a per-auction log and compute the running max downstream, which even lets me split the hot key across sub-partitions since maxes recombine. If instead I had an order-sensitive invariant like 'don't oversell inventory,' I'd funnel the key through a single leader and batch-apply. Locking the hot row is the thing I *don't* do."

## Connections
- [[system-design-concepts/commutative-aggregation]] — the preferred exit: order-free ops need no coordination, so contention dissolves into an append + deferred fold
- [[system-design-concepts/the-log-abstraction]] — the concrete mechanism for exit #1: per-key append-only log + materialized aggregate
- [[system-design-concepts/hash-vs-range-partitioning]] — partitioning is what makes *aggregate* load easy and is exactly what fails on a hot key
- [[system-design-concepts/global-rate-limiting]] — exit #3 in the wild: sharded "accurate" counters vs. a single "consistent" one
- [[theory/consistency-models]] — whether you *need* coordination is a consistency question; PACELC's "else latency-vs-consistency" is the everyday axis here
- [[system-design-concepts/event-time-vs-processing-time]] — the deferred aggregate is usually a *windowed* fold, inheriting watermark/cutoff concerns
- [[system-design-concepts/geospatial-indexing]] — the **spatial twin**: a hot *cell* (stadium/airport/surge) is a hot key in 2-D; aggregate location load shards, cell concentration doesn't

## Sources
- [[sources/docs/design-instagram-auction-mock-interview]] — the auction bid path; naming single-key contention as the crux, and `max`-log as the exit
- [design-instagram-auction-mock-interview.md](https://github.com/redblackcoder/interview-prep-raw/blob/master/docs/design-instagram-auction-mock-interview.md) — full mock-interview transcript
- [[sources/docs/design-uber-driver-allocation-mock-interview]] — the hot-cell analog (un-entered crux in that round)
