# RDS vs Key-Value Store: Choosing the Primary Data Store

The recurring storage-layer decision: back an application with a **relational database** (AWS RDS for PostgreSQL) or a **key-value store** (AWS ElastiCache, Redis/Valkey), both configured to persist. At staff+ the interviewer isn't testing "SQL vs NoSQL" — they want the reasoning primitives: what each store is *priced on*, what invariants it *guarantees*, and what it *costs* you when the workload changes.

## The one-sentence mental model

> **PostgreSQL is priced on cheap disk + compute + IO and sells flexible querying and hard durability. Redis is priced on expensive RAM and sells microsecond latency and high throughput on a hot working set.**

You are choosing which resource — *durable bytes on SSD* or *fast bytes in DRAM* — dominates your bill and your guarantees. Almost every pro, con, and cost delta is downstream of that single fact: a relational DB keeps the bulk on SSD (~$0.115/GB-mo) and pulls the hot subset into a RAM buffer cache; a KV store holds the **entire** dataset in DRAM, priced ~100–250× higher per GB (see [[system-design-concepts/cloud-database-cost-model]]).

## How they differ

| Dimension | RDS PostgreSQL (system of record) | ElastiCache Redis/Valkey (KV) |
|---|---|---|
| Primary resource billed | Compute + SSD storage + provisioned IOPS | RAM (node memory), or storage-GB-hr + ECPU (serverless) |
| Data model | Relational, typed schema, SQL, joins, ad-hoc queries, secondary indexes | Key → value + structures (hash, sorted-set, stream); O(1) by key, **no joins** |
| Consistency | ACID, strong, MVCC; synchronous Multi-AZ commit | Per-command atomic; **async** replication → weaker; failover can lose writes |
| Durability (RPO) | Near-zero (WAL + fsync + sync standby) | Non-zero — snapshot interval + replication lag (see [[theory/durability-rpo-rto]]) |
| Latency (p50 point read) | ~0.2–2 ms | ~50–300 µs |
| Write scaling | Single writer — vertical, or shard (Citus / app / Aurora Limitless); vanilla Aurora raises the ceiling but is still single-writer | Horizontal via cluster sharding (up to 500 shards) |
| Best role | System of record; anything relational, transactional, query-diverse | Cache, session, rate-limit, leaderboard, queue, derived/ephemeral state |

## Why "query flexibility" is the relational killer feature

PostgreSQL lets you answer questions you *didn't anticipate at design time* — arbitrary `JOIN`/`GROUP BY`/window functions, and any column can become a fast access path via a secondary index added after the fact. Redis inverts this: **the query is the key**. You model for the read up front; a new access pattern means a new key structure and a backfill, and there is no query planner. Every unplanned access degrades to an `O(N)` `SCAN` across the keyspace (`KEYS *` in production is an incident — it blocks the single command thread). If the business will ask unpredictable analytical questions, that is a relational (or warehouse) job.

## The persisted-store nuance

The comparison gets genuinely technical once you require *both* to persist, because **"Redis with persistence" ≠ "a durable database."** Postgres acknowledges a commit only after the WAL is `fsync`'d and (in Multi-AZ) a synchronous standby has it → RPO≈0. ElastiCache's realistic durability story is **async replication + periodic S3 snapshots + fast failover** — AOF is not supported once Multi-AZ auto-failover is on — so its RPO is "last snapshot + un-replicated writes," never provably zero. Detail in [[tech/aws-elasticache-redis]] and [[theory/durability-rpo-rto]].

## The default staff+ answer: use both

Rarely "pick one." The senior answer is **PostgreSQL as the durable system of record, Redis as a read-through/write-through cache and real-time layer in front of it** — a CQRS-lite split. Postgres owns correctness and arbitrary queries on cheap storage; Redis absorbs hot read traffic and latency-critical ops, so you only pay DRAM prices for the *hot slice*, not the whole dataset. Name the cache strategy (cache-aside vs write-through), the TTL, and the invalidation plan, and you've answered at staff level.

### Anti-patterns to call out
- **Redis as sole system of record for critical data** — accepting an unquantified non-zero RPO.
- **A large cold dataset entirely in Redis** — paying DRAM prices for bytes you rarely read (use data-tiering, or keep it in Postgres/S3).
- **Ad-hoc analytics on Redis** via `KEYS`/`SCAN` — that's a relational/warehouse job.
- **Postgres as a high-churn cache** — ephemeral writes create bloat/vacuum pressure Redis handles natively with TTL.

## Key points
- The choice is fundamentally *durable SSD bytes* vs *fast DRAM bytes* — resource economics, not a feature checklist.
- Relational wins on query flexibility, referential integrity, and cost-per-GB; KV wins on latency, write scale-out, and purpose-built structures.
- "Persisted Redis" survives restarts but does not give transactional durability — size the acceptable data-loss window explicitly.
- For a large system of record, RDS usually wins on cost *and* guarantees; Redis earns its premium only where a specific access pattern needs µs latency at high QPS.

## Interview angle

> "I frame it as economics: Postgres is priced on cheap SSD plus compute and sells flexible querying and hard durability; Redis is priced on expensive RAM and sells microsecond latency on a hot set. So for a system of record I default to RDS PostgreSQL — ACID, joins, ad-hoc queries, and the bulk on $0.11/GB SSD — and put Redis in front only for the latency-critical hot slice, as a cache or session/leaderboard/rate-limit store. The trap in 'just use Redis and persist it' is that its durability is async replication plus snapshots, not an fsync'd log, so the RPO is non-zero — fine for a cache, not for money."

## Connections
- [[system-design-concepts/cloud-database-cost-model]] — the four cost axes and the ~100–250× RAM-vs-SSD ratio that drives this decision
- [[tech/aws-rds-postgresql]] — the relational implementation: topologies, storage engines, durability knobs
- [[tech/aws-elasticache-redis]] — the KV implementation: engines, persistence semantics, data tiering
- [[theory/durability-rpo-rto]] — the durability contract that separates a system of record from a cache
- [[theory/copy-on-write-vs-mvcc]] — MVCC is *how* Postgres gives readers a consistent snapshot without blocking writers

## Sources
- [[sources/docs/rds-vs-kv-store-study-guide]] — full staff+ study guide (data model, durability, scaling, pricing, decision framework)
- [rds-vs-kv-store-study-guide.html](https://github.com/redblackcoder/interview-prep-raw/blob/main/docs/rds-vs-kv-store-study-guide.html) — §0–1 mental model & data model, §11 decision framework
