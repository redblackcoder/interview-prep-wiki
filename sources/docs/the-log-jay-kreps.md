---
source: docs/the-log-jay-kreps.md
source_url: https://github.com/redblackcoder/interview-prep-raw/blob/master/docs/the-log-jay-kreps.md
author: Jay Kreps
type: doc
date_extracted: 2026-08-19
topic: system-design-concepts / theory
---

# The Log: A Unifying Abstraction (Jay Kreps)

## Key Ideas
- **The log = append-only, totally-ordered sequence of records.** Its order defines a logical
  clock decoupled from any physical clock — the property that makes it work across distributed systems.
- **State Machine Replication Principle:** identical deterministic processes + same inputs in the
  same order → same output and same end state. So "make N machines agree" reduces to "implement one
  consistent log to feed them." The log's job is to **squeeze non-determinism out of the input stream.**
- **Consensus is really log-building.** Multi-Paxos, ZAB, Raft, Viewstamped Replication all model
  maintaining a distributed consistent log. A log (sequence of decisions) is a more natural abstraction
  than a single-value register.
- **Table/log duality.** A table is the current state; the log is the ordered changelog. Apply the log
  → get the table; the log can also derive *any* view and reconstruct *every* past state (self-backup).
- **The log as the org's data-integration hub.** Point every system at one central log instead of
  building O(N²) point-to-point pipelines → O(N). Producers publish clean, canonical feeds; consumers
  subscribe at their own pace (the log is a durable buffer).
- **Stateful stream processing via table/log duality.** Keep local index state, journal a changelog of
  it → restore on crash by replaying. The processor's state is itself a log others can subscribe to.
- **Unbundling the database.** A system = **the log + a serving layer** (index). The log handles
  consistency, replication, commit semantics, subscription, restore, rebalancing; the serving layer just
  owns the query API + index. Serving nodes can be *leaderless* since the log is the source of truth;
  clients get read-your-writes by passing the write's log timestamp.
- **Log compaction** for keyed data: drop records superseded by a newer value for the same key → bounded
  size while still a complete backup of the latest state.

## My Understanding
- This is the "why" under everything else I read. Kafka (the log), Lambda (immutable master dataset +
  recompute), Kappa (log = stream, replay to recompute) are all *the same idea* — the log as the system
  of record, everything else a derived projection.
- **Table/log duality** is the crispest single idea: my billing "immutable event store + recompute" and
  a materialized dashboard view are the *same data* seen two ways (log at rest vs. table at rest).
- **SMR** finally connects consensus to logs for me: Raft/ZAB aren't abstract — they exist to produce the
  one ordered input log that keeps replicas deterministic. KRaft is literally this for Kafka's metadata.
- The **log + serving-layer split** reframes "which database" as "which *index* on the shared log" — a
  cleaner way to reason about a whole architecture (and about my termination-orchestrator / assistant
  subsystems as consumers of one event log).

## Open Questions
- AP (eventually-consistent) log vs. CP log: when is a Dynamo-style "log that redelivers and pushes dedup
  to the subscriber" the right call over a strongly-consistent Kafka/BookKeeper log?
- Practical cost/latency of "recompute from the complete log" at real scale — where does the snapshot +
  bounded-log-window optimization become mandatory?

## Connections
- Feeds: [[sources/docs/kafka-101-bytebytego]] (Kafka is Kreps' log productized),
  [[sources/docs/how-to-beat-the-cap-theorem]] (Lambda's master dataset = the log),
  [[sources/docs/streaming-101-world-beyond-batch]] ("log is another word for stream").
- Builds on: [[wiki/theory/consensus-raft]] — consensus algorithms exist to build the consistent log.
- Relates to: [[wiki/theory/consistency-models]], [[wiki/system-design-concepts/leaderless-vs-leader-based]].
- Anchors wiki pages: [[wiki/system-design-concepts/the-log-abstraction]],
  [[wiki/system-design-concepts/table-log-duality]], [[wiki/theory/state-machine-replication]].
