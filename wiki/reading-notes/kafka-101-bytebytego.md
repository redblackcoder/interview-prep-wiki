---
source: ../../sources/references/kafka-101-bytebytego.md
author: Stanislav Kozlovski (via ByteByteGo)
type: doc
date_extracted: 2026-08-19
topic: tech / system-design-concepts
---

# Kafka 101

## Key Ideas
- **Kafka is a distributed, replicated, append-only log, not a queue.** The log
  (immutable, O(1) tail append/read, linear I/O) is the primitive; it was chosen
  because linear reads/writes are what HDDs + the OS pagecache are fast at.
- **Durability is a tunable, not a constant.** `acks` (0 / 1 / all) × `min.insync.replicas`
  is the dial. `acks=all` + `min.insync.replicas≥2` = "don't lose acknowledged data,"
  trading write availability for durability during partitions.
- **Producer/consumer decoupling via disk persistence.** Data isn't deleted on consume,
  so slow consumers can't back-pressure producers, and many independent consumer groups
  can each hold their own cursor and replay. Offsets live in `__consumer_offsets`; the
  partition leader is the group coordinator.
- **Ordering is per-partition only**, and one partition → at most one consumer per group.
  So ordering guarantee and parallelism are the *same dial* (partition count).
- **Consensus moved in-house: ZooKeeper (Zab) → KRaft (Raft).** Cluster metadata is itself
  modeled as a replicated log (`__cluster_metadata`); a Raft quorum of controllers replaces
  the external ZK dependency.
- **Exactly-once = effectively-once.** The idempotent producer dedups retries by
  `(producer-id, partition, sequence)` at the broker — the same "dedup by a key at the
  sink" pattern. (Transactions/atomic commit deferred — see Open Questions.)
- **Tiered storage** pushes cold segments to object storage (S3), fixing slow log recovery,
  IOPS contention from historical reads, and expensive rebalancing.

## My Understanding
- The unlock is **"queue vs. log."** A queue forgets; a log remembers and lets N readers
  each hold their own offset. That single property (persist + offset) is *why* Kafka
  decouples producers from consumers and why replay and multi-consumer "just work."
- A client-side no-loss contract (retry until acked) **is** `acks=all` +
  `min.insync.replicas` one layer down: the broker only ACKs once the ISR has persisted,
  so the client can safely stop retrying.
- Partition count is my throughput knob **and** my ordering/parallelism ceiling — not two
  separate decisions.
- Idempotent-producer dedup by `(PID, seq)` is the concrete version of the insight
  that exactness comes from *at-least-once delivery + dedup by an id*, not from magic.

## Open Questions
- Transactions / `sendOffsetsToTransaction` (atomic consume-process-produce): deliberately
  skipped for now. Revisit when I need the *consumer-crash* half of exactly-once.
- How does the `(PID, seq)` window behave across producer restarts (PID changes) without a
  stable `transactional.id`? (Suspect: idempotence guarantee resets per session.)

## Connections
- Builds on: [state machine replication](../theory/state-machine-replication.md) — KRaft is Raft applied to the
  metadata log; the log *is* the SMR input stream.
- Relates to: [consensus raft](../theory/consensus-raft.md), [consistency models](../theory/consistency-models.md),
  [durability math](../theory/durability-math.md) — `acks`/ISR is the durability-vs-availability tradeoff made concrete.
- Relates to: [leaderless vs leader based](../system-design-concepts/leaderless-vs-leader-based.md) — Kafka is strictly
  leader-based per partition (contrast Dynamo-style leaderless).
- Feeds: [the log jay kreps](the-log-jay-kreps.md), [how to beat the cap theorem](how-to-beat-the-cap-theorem.md),
  [streaming 101 world beyond batch](streaming-101-world-beyond-batch.md) — Kafka is the replayable log those assume.
- Anchors wiki page: [kafka](../tech/kafka.md), [exactly once semantics](../system-design-concepts/exactly-once-semantics.md).
