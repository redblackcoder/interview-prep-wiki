# Transactional Outbox (+ Sweeper)

How to keep a second system (cache, queue, search index) in sync with a database **without a distributed transaction**. In the same local transaction as the business write, insert an **outbox row** describing the side effect; a relay publishes outbox rows to the second system and marks them done. The DB stays the source of truth; the second system is eventually consistent with it.

## The one-sentence mental model

> **Write the intent in the same transaction as the data, publish it afterward at-least-once, and design the consumer so duplicates are harmless — then the secondary store is a hint you can always rebuild, never a truth you can lose.**

## How it works
1. `BEGIN; INSERT execution …; INSERT outbox(kind='push_redis', ref=id) …; COMMIT;` — both or neither.
2. A relay (poller, or CDC like Debezium tailing the WAL) reads unpublished outbox rows, writes to Redis/Kafka, marks them published.
3. Crash between publish and mark ⇒ re-publish ⇒ **consumers must be idempotent** (upsert by id, or the downstream claim is a CAS).

## The gap outbox alone doesn't close
"Published" means the relay's write was **acked**, not that it's durable. Redis with async replication can fail over and lose acked writes; the outbox still says done. Two fixes:
- **Sweeper against the source of truth** — periodically query the DB for "should be in the secondary but may not be" (e.g. `state='QUEUED' AND scheduled_at < now()+10s`) and re-push. Catches holes anywhere in the window, unlike a "last-seen" watermark that only catches tail loss.
- **Make over-pushing free** — if the consumer's first action is a conditional claim ([[system-design-concepts/lease-and-fencing]]), duplicate entries cost nothing, so the sweeper can be aggressive.

Net: the secondary store's loss costs **latency**, never **correctness**.

## Key points
- Every entry point writes the DB first — including "fast paths" like execute-now. Skipping straight to the cache means a failover loses work you already acked to the user.
- Poll-based relay is simple but adds DB load; CDC avoids polling but adds infrastructure.
- Outbox rows need cleanup (TTL / partition drop) or they become a hot, ever-growing table.
- Inbox pattern is the mirror: the consumer records processed message ids to dedup.

## Interview angle

> "I don't dual-write. The execution insert and an outbox row commit together; a relay pushes to Redis at-least-once. Redis is only a latency hint — a sweeper re-pushes anything due soon that's still queued, and because the dispatcher's first step is a CAS claim, duplicates are free. Losing Redis costs me a few seconds, never a job."

## Connections
- [[system-design-concepts/lease-and-fencing]] — the idempotent consumer that makes re-publish safe
- [[system-design-concepts/exactly-once-semantics]] — at-least-once publish + idempotent sink
- [[system-design-concepts/distributed-job-scheduler]] — DB → Redis dispatch cache
- [[system-design-concepts/table-log-duality]] — CDC relay is reading the table's log
- [[tech/aws-elasticache-redis]] — async replication is why acked ≠ durable

## Sources
- [[sources/docs/design-job-scheduler-mock-interview]] — outbox, watermark vs sweeper, execute-now bypassing the DB
