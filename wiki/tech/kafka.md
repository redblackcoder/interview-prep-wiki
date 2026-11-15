# Apache Kafka

Kafka is a **distributed, replicated, append-only log** productized as the "central nervous system" for an organization's data — the concrete implementation of [the log as a unifying abstraction](../system-design-concepts/the-log-abstraction.md). Optimized for millions of messages/sec and terabytes of retained data on cheap linear-I/O storage.

## The one-sentence mental model

> **Not a queue — a durable, replayable, partitioned log. Producers append; many consumer groups each hold their own offset and replay independently; durability is a dial (`acks` × `min.insync.replicas`), ordering and parallelism are the same dial (partition count).**

## How it works

### Storage: the log, tuned for disk
Topics are split into **partitions**; each partition is an append-only log of records addressed by a monotonic **offset**. Kafka persists everything to disk and leans on **linear reads/writes** (fast even on HDDs) + the OS **pagecache**. It keeps a single binary format across memory/disk/network, enabling **zero-copy** (though TLS in production largely negates it — and CPU is rarely the bottleneck anyway).

### Replication & durability
Each partition is replicated to N replicas; one is **leader** (sole writer), the rest are followers, tracked as the **in-sync replica (ISR)** set. Producer durability is tunable:

| `acks` | ACK when… | Guarantee |
|---|---|---|
| `0` | never waits | fire-and-forget (can lose) |
| `1` | leader persists | lost if leader dies pre-replication |
| `all` (default) | all **ISR** persist | no loss of acknowledged data |

`min.insync.replicas` stops `acks=all` from silently degrading to `acks=1` when the ISR shrinks to one — the write fails instead. **`acks=all` + `min.insync.replicas≥2` = the "don't lose acknowledged data" setting** (and the mechanism behind the client write-then-ACK no-loss contract).

### Consumers
Clients read from a partition (any replica, usually nearest). **Consumer groups** divide partitions among members; **one partition → at most one consumer per group** (so ordering is per-partition and parallelism is capped by partition count). Progress is stored as committed **offsets** in `__consumer_offsets`; the partition leader acts as **group coordinator**. Because data isn't deleted on consume, **many independent groups replay the same topic**, and a slow consumer can't back-pressure producers.

### Metadata consensus: ZooKeeper → KRaft
Historically ZooKeeper (Zab) held cluster metadata and did controller election. **KRaft** replaces it: cluster metadata is itself a **replicated log** (`__cluster_metadata`) managed by a **Raft** quorum of controllers; the leader is the active controller, others are hot standbys, and brokers stay current by tailing the topic. (Production-ready in 3.3; ZK removed in 4.0.)

### Tiered storage
Colocating all data on brokers hurts at scale (slow log recovery after ungraceful shutdown, historical reads exhausting HDD IOPS and competing with producers, huge rebalances). **Tiered storage** offloads cold segments to object storage (S3); leaders tier, both leaders and followers serve historical reads from the object store — fixing recovery, IOPS contention, and rebalance cost.

### Ecosystem
- **Kafka Connect** — plug-and-play **source/sink connectors** (Postgres, S3, Elastic, Snowflake…) for O(N) data integration; uses internal topics + consumer-group protocol for config/offsets/failover.
- **Kafka Streams** — a *client library* (not a cluster) for stateful stream processing; local state stores + changelog topics ([table log duality](../system-design-concepts/table-log-duality.md)); supports **exactly-once** Kafka→Kafka (`exactly_once_v2`).
- **Cruise Control** — automated partition rebalancing (an NP-hard bin-packing problem).

## Key points
- **Log, not queue** — the offset-per-consumer + retention model is the whole reason replay and multi-consumer work.
- **Ordering is per-partition only**; there's no global order across partitions. Partition count is the joint throughput/ordering/parallelism knob.
- **Durability is configuration**, not a fixed property: `acks` × `min.insync.replicas`. Know the failure each level allows.
- **Leader-based per partition** — contrast Dynamo-style [leaderless](../system-design-concepts/leaderless-vs-leader-based.md) stores.
- **KRaft = Raft on the metadata log** — a clean example of [state machine replication](../theory/state-machine-replication.md).
- Exactly-once is **effectively-once** and only Kafka→Kafka — external sinks still need idempotency ([exactly once semantics](../system-design-concepts/exactly-once-semantics.md)).

## Interview angle

> "Kafka is a distributed, replicated, append-only log — not a queue. Producers append to partitions; each partition is an ordered log addressed by offset, and consumers track their own offset, so many consumer groups can replay the same data independently and slow consumers never back-pressure producers. Durability is a dial: `acks=all` plus `min.insync.replicas` means the broker only acknowledges once the in-sync replicas have persisted — that's the no-loss contract. Ordering is per-partition, and partition count is simultaneously your ordering guarantee and your parallelism ceiling. It's strictly leader-based per partition, and modern Kafka replaces ZooKeeper with KRaft, which is just Raft applied to a metadata log. At scale, tiered storage offloads cold data to S3 so historical reads don't starve producers of IOPS."

## Connections
- [the log abstraction](../system-design-concepts/the-log-abstraction.md) — Kafka is this abstraction made real
- [consensus raft](../theory/consensus-raft.md) / [state machine replication](../theory/state-machine-replication.md) — KRaft is Raft on the metadata log
- [leaderless vs leader based](../system-design-concepts/leaderless-vs-leader-based.md) — Kafka partitions are leader-based (contrast Dynamo)
- [exactly once semantics](../system-design-concepts/exactly-once-semantics.md) — idempotent producer + transactions + read_committed
- [table log duality](../system-design-concepts/table-log-duality.md) — Kafka Streams state stores, changelog topics, log compaction
- [event time vs processing time](../system-design-concepts/event-time-vs-processing-time.md) — Kafka Streams event-time windowing + watermarks
- [lambda vs kappa](../system-design-concepts/lambda-vs-kappa.md) — the replayable log that makes Kappa (single-pipeline) feasible
- [durability math](../theory/durability-math.md) — `acks`/ISR is the operational side of the durability math
- [aws elasticache redis](aws-elasticache-redis.md) — contrast: Redis as ephemeral state vs Kafka as durable log

## Sources
- [kafka 101 bytebytego](../reading-notes/kafka-101-bytebytego.md) — full architecture tour (log, acks/ISR, consumers, KRaft, tiered storage, Connect, Streams)
- [kafka-101-bytebytego.md](../../sources/references/kafka-101-bytebytego.md) — Stanislav Kozlovski / ByteByteGo (2024)
- [the log jay kreps](../reading-notes/the-log-jay-kreps.md) — why Kafka was built (LinkedIn data integration, the log concept)
