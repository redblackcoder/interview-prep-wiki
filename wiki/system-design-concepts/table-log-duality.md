# Table/Log Duality

A **table** and a **log** are two views of the same information: the log is the ordered sequence of *changes*; the table is the *current state* those changes fold up to. "Apply the log → get the table; record the table's changes → get the log." This duality is the engine behind changelogs, materialized views, CDC, and fault-tolerant stream state.

## The one-sentence mental model

> **The log is every debit and credit; the table is the account balance. The log is the more fundamental one — from a complete log you can rebuild the table *and* every past version of it; from a table alone you've lost history.**

## How it works

```
   LOG (changes, ordered)              TABLE (state = fold of log)
   +(alice, Chicago, t1)               alice → Atlanta   (latest wins)
   +(alice, Atlanta, t2)               bob   → …
   +(bob,   …,       t3)     ──fold──▶
```

- **Log → table:** replay changes in order, keep the latest value per key. A `reduce`/fold over the log.
- **Table → log:** capture each update as a change record and publish a **changelog** — exactly what a near-real-time replica needs (this is **Change Data Capture**).
- Because the log holds *all* changes, it's a **backup of every previous state**, not just the current one (contrast a mutable DB, which destroys the old value on update).

### Where it pays off: fault-tolerant stream state

Stateful stream processing (counts, windowed aggregations, stream-table joins) needs local state that survives crashes. The duality gives the mechanism: keep state in a **local index** (RocksDB/LevelDB/Lucene), and **journal a changelog** of that index to the log. On crash, **replay the changelog to restore state.** The processor's state is itself a log others can subscribe to. (This is Kafka Streams' state stores.)

### Bounding the log: compaction

You can't keep all changes forever. **Log compaction** keeps the *latest* record per key and discards superseded ones — so the log stays a complete backup of the *current* state (you lose the ability to replay *old* states, but not the latest). Contrast **retention windows** (drop anything older than N days), used for pure event data.

## Key points
- The log is primary; the table is a **derived, cacheable projection**. Multiple tables/indexes can be derived from one log.
- **CDC = table→log**; **materialized view / replica = log→table.** Same duality, opposite directions.
- Analogy to **version control**: the patch history is the log, your working checkout is the table, and you replicate by pulling patches (the log), not snapshots.
- Compaction (keep latest-per-key) vs retention (drop-by-age) is the keyed-data vs event-data cleanup fork.
- This is the concrete justification for "billing recomputes from an immutable event store": the event log is the source of truth; the billing table is a fold you can always rebuild — see [[system-design-concepts/lambda-vs-kappa]].

## Interview angle

> "A table and a log are dual: the log is the ordered list of changes, the table is the current state you get by folding those changes, keeping the latest per key. The log is more fundamental — it can rebuild the table and every historical version, whereas the table alone has thrown away history. That's why change-data-capture (table→log) and materialized views or replicas (log→table) are the same idea in opposite directions. Practically, it's how stream processors get fault-tolerant state: keep a local index, journal its changelog to the log, and replay to recover. And log compaction — keep the latest value per key — is how you bound the log while still holding a full backup of current state."

## Connections
- [[system-design-concepts/the-log-abstraction]] — duality is *why* every table/index can be a projection of the log
- [[theory/state-machine-replication]] — the table is the state a replica reaches by folding the command log
- [[tech/kafka]] — Kafka Streams state stores + log compaction are this duality in production
- [[system-design-concepts/lambda-vs-kappa]] — "recompute the view from the immutable log" relies on log→table
- [[theory/copy-on-write-vs-mvcc]] — another "keep history to reconstruct past states" theme (versioned snapshots)
- [[theory/durability-rpo-rto]] — event-sourcing (log of changes) vs snapshot (table), and replay hazards

## Sources
- [[sources/docs/the-log-jay-kreps]] — "Changelog 101: Tables and Events are Dual" and stateful stream processing
- [the-log-jay-kreps.md](https://github.com/redblackcoder/interview-prep-raw/blob/master/docs/the-log-jay-kreps.md) — Jay Kreps, "The Log" (2013)
- [[sources/docs/kafka-101-bytebytego]] — log compaction and Kafka Streams local state
