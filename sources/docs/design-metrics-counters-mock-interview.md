---
source: interview session (raw transcript not published)
type: doc
date_extracted: 2026-09
topic: system-design-concepts
---

# Design a Counters / Metrics Pipeline — System-Design Round

Distilled from a ~45-min system-design round: **"a `track.increment(metric)` library ships with every service; design everything after the call — how the data is sent, collected, stored, and queried."** Restricted to **counters** (no latency/percentiles). Feed/alerting/visualization were out of scope, so the round funneled onto **the ingest pipeline: aggregation placement, agent topology, transport semantics, and where a queue lives.**

## Key Ideas

- **The crux is a fan-in reduction across a *deliberately lossy* pipeline, and the organizing principle is "reliability increases as you move inward."** UDP at the app→agent hop is *"kind of the opposite of having a queue"*, and both are right given the SLA — the data is operational, not billing, so **shedding at the edge is correct**, while **buffering/persisting in the core** (queue in front of the DB) is also correct. That gradient — shed where volume is highest and value-per-event lowest, persist where each message already represents thousands of aggregated events — is the framing to state first: it derives UDP-at-edge, idempotent writes, and queue-in-front-of-DB as consequences. → [[wiki/system-design-concepts/edge-shed-vs-core-durability]].

- **Storage-engine reasoning.** Reject a B-tree RDBMS for the write path (append-heavy, index-rebalance cost), reach for **LSM-tree**-backed stores, and compare **TSDB vs Cassandra vs relational** on the right axes (write amplification, recent-window reads served from the in-memory memtable, compaction). → [[wiki/system-design-concepts/rds-vs-key-value-store]], [[wiki/system-design-concepts/hash-vs-range-partitioning]].

- **Sidecar vs DaemonSet.** Running the agent as a separate process keeps its memory/bugs away from the service, and the **resource-multiplication** axis (N sidecars per node vs 1 daemon) matters. But runtime memory isn't what decides it for a shipping agent; two other axes do: **(1) central upgradeability** — a sidecar's version is pinned to the app deploy (ship an agent fix ⇒ redeploy every service or rely on webhook re-injection; teams pin old versions), whereas a DaemonSet is upgraded by the platform team independently; this is why real agents (Datadog, otel-collector agent mode, fluent-bit, node_exporter) are DaemonSets. **(2) shared-dependency blast radius** — a DaemonSet mixes tenants, so one flooding pod degrades everyone on the node ⇒ needs per-source fairness; a sidecar is naturally isolated. → [[wiki/system-design-concepts/sidecar-vs-daemonset]].

- **Local IPC transport: backpressure vs load-shedding.** Under a tight-loop `track.increment` bug, UDP would *drop* while TCP would back up and OOM the agent. The real axis is **backpressure vs load-shedding**, and a **Unix domain socket lets you choose** (`SOCK_STREAM` = reliable + backpressure into the app; `SOCK_DGRAM` = message framing, drop-on-full). For a metrics path that must never harm the app you want **fire-and-forget datagrams** (UDP loopback for a DaemonSet via host IP, or datagram UDS for a sidecar via shared volume), non-blocking, drop-on-full, with client-side batching. HTTP's request/response couples app latency to the agent and is the wrong tool on the hot path (reserve it for agent→collector). → [[wiki/system-design-concepts/local-ipc-transports]].

- **The dedup / double-count seam.** An agent retry to a different collector double-counts. "Kafka message-id + consumer-group gives single delivery" overstates the guarantee — Kafka's idempotent producer dedups only within a producer session on one partition (producer-id + sequence), **not** across different agents/sessions, and consumer groups don't give exactly-once *to the DB*. The clean close: make the **write idempotent** — each `(metric, source, window-start)` maps to a fixed cell and a retry **overwrites** rather than adds, so duplicates are harmless and you need no exactly-once delivery at all. This is the [[wiki/system-design-concepts/commutative-aggregation]] move (counters are idempotent per cell). → [[wiki/system-design-concepts/exactly-once-semantics]].

- **Queue vs direct, and front-vs-behind the collector.** A queue absorbs bursts, protects the DB, and fans out — but the agents' 1s batching **is already a distributed buffer**, so the queue's marginal value is specifically **DB-outage durability + multi-consumer fan-out + smoothing to the bottleneck**, not "buffering." Placement matters: **the queue's job is to protect the bottleneck (the DB), so it belongs in front of the bottleneck — i.e. behind a thin collector**, not in front of the collector. Queue-in-front forces a Kafka client into every service in every language (bad for "a library that ships with everything"); a thin collector shields clients with a dumb protocol and terminates into the queue as the internal backbone. → [[wiki/system-design-concepts/queue-placement]].

- **Size it.** Services × metrics × emit-rate → writes/s, storage sizing, cardinality, and what 1s pre-aggregation actually saves. One sentence — *"N services × M metrics × emit-rate → writes/s → does one TSDB shard hold it?"* — grounds the entire scaling discussion.

- **Event-time vs processing-time.** Stamp the **event's timestamp in the payload** (not arrival time). Late-arriving points reshape an already-observed window, and clock skew keeps the answer accurate to ~tens of ms. → [[wiki/system-design-concepts/event-time-vs-processing-time]].

- **Metric-specific failure modes.** Beyond retries, collector-down, backpressure, DB-can't-keep-up, sharding, and read replicas: **cardinality explosion** and **agent versioning/rollout**.

## Connections
- The organizing principle: [[wiki/system-design-concepts/edge-shed-vs-core-durability]] — shed at the edge, persist in the core; where the UDP-vs-queue tension resolves.
- Agent topology: [[wiki/system-design-concepts/sidecar-vs-daemonset]] — upgradeability + blast-radius, not just runtime memory.
- The app→agent hop: [[wiki/system-design-concepts/local-ipc-transports]] — HTTP loopback vs UDS vs UDP; backpressure vs load-shedding.
- Queue vs direct, front vs behind: [[wiki/system-design-concepts/queue-placement]] — the queue guards the bottleneck.
- The correctness close: [[wiki/system-design-concepts/commutative-aggregation]] (idempotent per-cell counters) + [[wiki/system-design-concepts/exactly-once-semantics]] (why at-least-once + idempotent write beats exactly-once delivery).
- Time semantics: [[wiki/system-design-concepts/event-time-vs-processing-time]] — payload time, late-arrival window mutation.
- Storage: [[wiki/system-design-concepts/rds-vs-key-value-store]], [[wiki/system-design-concepts/hash-vs-range-partitioning]]; recent-window reads served from the memtable.
- Adjacent metrics study (same problem family, complementary): [[wiki/system-design-concepts/metrics-pull-vs-push]], [[wiki/system-design-concepts/red-metrics-exposition]], [[wiki/system-design-concepts/mergeable-metrics-and-quantiles]], [[wiki/system-design-concepts/counter-reset-and-restart-recovery]].
- Grounding the sizing sentence: [[wiki/theory/latency-numbers]].

## Sources
- [[sources/docs/metrics-collection-observability-self-study]] — the self-study that pre-loaded the pull/push, RED, mergeable-quantile, and restart-recovery background this round drew on.

## Concepts flagged for wiki (created with this extract)
- **Edge-shed vs core-durability** — reliability increases inward; the UDP-vs-queue gradient. *(new: [[wiki/system-design-concepts/edge-shed-vs-core-durability]])*
- **Sidecar vs DaemonSet** — upgradeability + blast-radius decide it, not runtime memory. *(new: [[wiki/system-design-concepts/sidecar-vs-daemonset]])*
- **Local IPC transports** — HTTP loopback vs UDS vs UDP; backpressure vs load-shedding. *(new: [[wiki/system-design-concepts/local-ipc-transports]])*
- **Queue placement** — queue vs direct, and in-front-of vs behind the collector; guard the bottleneck. *(new: [[wiki/system-design-concepts/queue-placement]])*
