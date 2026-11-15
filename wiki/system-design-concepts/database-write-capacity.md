# Database Write Capacity (Postgres vs DynamoDB)

How to size a write-heavy store without conflating the numbers. "40k writes/s" hides five different quantities for Postgres and two for DynamoDB — and the one that bites is usually not the one quoted.

## The one-sentence mental model

> **Postgres pays per commit and per page; DynamoDB pays per item-write and per partition key. Batching cuts Postgres commits but not row writes; DynamoDB has no commits to amortize, so every item write is billed and the only hard wall is one hot key.**

## Postgres: five separate numbers
| Measure | What it is | How to estimate |
| --- | --- | --- |
| **Row writes/s** | rows inserted/updated | cohorts × rate (e.g. 4 transitions × 10k = 40k/s) |
| **Transactions/s** | commits — CPU + WAL fsync cost | row writes ÷ rows per tx. **Batching only helps if batches are actually large** |
| **IOPS** | WAL flushes + dirty pages written at checkpoint | ≠ row writes; a hot page updated 100× is written once per checkpoint. Measure. Watch gp3 caps ([aws rds postgresql](../tech/aws-rds-postgresql.md)) |
| **Memory** | hot working set + indexes in buffer cache | live rows × row size + index size; must fit in RAM |
| **Disk** | rows × size × retention + WAL | partition by time; archive old partitions |

- Planning number: a large instance does roughly **10–20k write tx/s** — benchmark before trusting it. Shards = peak tx/s ÷ per-shard budget, with headroom.
- **Batch size is a property of the producer's event rate.** A producer seeing 3 events/s produces 3-row batches no matter what; aggregate through a per-shard writer to get hundreds of rows per commit.
- Hidden costs of high-churn updates: MVCC dead tuples, HOT updates blocked by indexed columns, xid wraparound at high tx/s, WAL volume. See [postgres as a work queue](postgres-as-a-work-queue.md).

## DynamoDB: capacity units
- **1 WCU** = one write/s of an item ≤ 1 KB. Failed conditional writes still cost. Transactional writes cost 2×.
- **1 RCU** = one strongly consistent read/s ≤ 4 KB; eventually consistent = half.
- **Per partition key ≈ 1,000 WCU / 3,000 RCU** — the one thing that doesn't scale; spread hot keys ([hot key write contention](hot-key-write-contention.md)).
- Priced per request, not per machine — cost grows linearly with writes, so it punishes chatty patterns (per-job heartbeats: 200k WCU) and rewards coarse ones (per-driver: 200 WCU). Check current pricing; on-demand runs a few × provisioned.
- No server-side clock for leases (skew margin needed); TTL deletion is lazy, so it can't be used for lease expiry; global tables are last-writer-wins, so no cross-region CAS.

## Choosing by load and shape
| Pick | When |
| --- | --- |
| **Postgres / Aurora** | queries, filters, joins, sorted scans, UI indexes; total writes ≲ 50k rows/s across a handful of shards; a 10–120 s failover pause is acceptable |
| **DynamoDB** | key-only access (get / put / CAS); very high or spiky writes; no failover window tolerated |

Small, key-only, high-availability state (leases, fences) *fits* DynamoDB — but at hundreds of writes/s, **load is not the reason to use it**; availability is.

## Interview angle

> "I size rows and transactions separately. Forty thousand row writes a second is fine for five Postgres shards only if commits carry hundreds of rows — and a driver with a thousand ten-minute jobs only sees about three events a second, so I aggregate status per shard before writing. IOPS aren't row writes; hot pages coalesce until checkpoint. Working set is nine million rows, a few GB — fits in RAM. In DynamoDB the same load is WCU, billed per item with no batching discount, and the only wall is a hot key. So I'd use Dynamo for key-only state that must never pause, not to absorb volume Postgres already handles."

## Connections
- [distributed job scheduler](distributed-job-scheduler.md) — the worked example (40k rows/s, ~370 tx/s after aggregation)
- [postgres as a work queue](postgres-as-a-work-queue.md) — the Postgres-specific failure modes under churn
- [cloud database cost model](cloud-database-cost-model.md) — the instance/storage/IO cost axes
- [rds vs key value store](rds-vs-key-value-store.md) — the broader relational-vs-KV decision
- [hot key write contention](hot-key-write-contention.md) — DynamoDB's per-key limit is this problem
- [littles law](../theory/littles-law.md) — where the live-row count (working set) comes from
- [aws rds postgresql](../tech/aws-rds-postgresql.md) — gp3/io2 IOPS limits, Aurora

## Sources
- [distributed job scheduler](../problems/system-design/distributed-job-scheduler.md) — Postgres vs DynamoDB capacity; rows vs transactions
