# Queue Placement (Whether, and Where, to Buffer)

Two questions that look separate but share one answer. **Whether** to add a durable queue to an ingest pipeline at all, versus going agent → collector → DB directly. And **where** to put it — *in front of* the collector (agents produce to the queue; the collector consumes) or *behind* it (a thin collector produces to the queue; a writer drains to the DB). The resolving idea for both: **a queue's job is to protect the bottleneck, so it belongs directly in front of the bottleneck — which in a metrics pipeline is the database, not the collector.**

## The one-sentence mental model

> **Add a queue when you need to shield the DB from bursts/outages or fan out to multiple consumers — not merely "to buffer," because edge batching already buffers. And place it *behind* a thin collector (in front of the DB), so clients speak a dumb ubiquitous protocol while the queue guards the real bottleneck and enables fan-out.**

## Whether: queue vs direct

**Direct (agent → collector → DB)** is simpler, cheaper, lower-latency, fewer things to run. The collector must then handle backpressure itself (bounded in-memory buffer, drop, or push back), and write-path availability is coupled to collector+DB health. Fine when there's one consumer and bursts are bounded.

**Add a queue** for three *specific* wins — and note what it is **not** for:
- **Smoothing to the bottleneck:** consumers drain at the DB's sustainable write rate; spikes land in the log, not on the DB.
- **Durability across outages/deploys:** survives a DB blip or a collector redeploy without loss.
- **Fan-out:** multiple independent consumer groups (TSDB, anomaly detection, cold storage) read the same stream without touching producers.
- **NOT "buffering" generically:** the agents' ~1s pre-aggregation is *already* a distributed buffer. Don't justify a queue with buffering you already have; justify it with durability + fan-out + DB-rate-smoothing.

The judgment call: with a lossy-tolerant, minutes-latency SLA and edge batching, it's legitimate to **start direct** (collector with a bounded buffer, drop under pressure) and **introduce the queue when you actually need DB-outage durability or a second consumer.** Saying that shows you're not cargo-culting Kafka.

## Where: in front of vs behind the collector

```
 IN FRONT:  agent ──► [QUEUE] ──► collector/consumer ──► DB
 BEHIND:    agent ──► thin collector ──► [QUEUE] ──► writer ──► DB
```

**Queue in front** (agents are producers): the queue is the durable front door, so ingest availability = queue availability, and collectors scale/redeploy freely as consumers. **But** it forces a **Kafka client into every service in every language** — broker discovery, auth, a heavy client lib — which is exactly what you don't want to push into "a library that ships with every service." And it *still* leaves the DB unprotected unless the consumer→DB step rate-limits.

**Queue behind** (collector is a producer): a **thin collector is a cheap, ubiquitous ingress** (dumb HTTP/UDS) that shields agents from Kafka entirely — validate, normalize, pre-aggregate, then produce to the queue; a writer drains to the DB at a safe rate, and this is where the **idempotent write** ([[system-design-concepts/commutative-aggregation]]) naturally lives, right before the DB. This is the common production topology (edge ingress → Kafka backbone → writers → store).

**The deciding principle:** the queue exists to **protect the bottleneck (the DB) and enable fan-out**, and both jobs are done right in front of the DB — i.e. *behind* the collector. "It doesn't matter where" is the wrong answer; ask *what is the queue's one job* and it locates itself.

## Key points
- **A queue guards the thing immediately downstream of it.** Put it in front of whatever you're protecting — here the DB.
- **Keep clients dumb.** A thin collector terminating a universal protocol beats a Kafka client in every service; that alone often decides "behind."
- **Edge batching is already a buffer** — the queue's marginal value is durability + fan-out + rate-smoothing, not buffering.
- **A queue smooths bursts but can't fix a deficit** — if sustained write rate exceeds DB capacity, the queue only grows; you still need to shed/aggregate-more/add-capacity ([[system-design-concepts/serving-constrained-resources]]).
- **Idempotency belongs at the DB write**, not in queue delivery semantics: retries become harmless overwrites by `(metric, source, window)` — you don't need exactly-once delivery ([[system-design-concepts/exactly-once-semantics]]).
- **A log is a throughput/priority substrate, not a delay-queue** — don't model "write this later on a schedule" as a parked partition.

## Interview angle

> "First, do I even need a queue? My agents already batch a second of data, so I'm not adding one 'to buffer' — I add it for three things: smoothing writes to the database at its sustainable rate, surviving a DB or collector outage, and fanning out to other consumers. Given a lossy, minutes-latency SLA I might even start direct with a bounded collector buffer and add the queue when durability or a second consumer actually shows up. On placement: the queue's job is to protect the bottleneck, and the bottleneck is the database, not the collector — so it goes behind a thin collector, right in front of the DB. That also keeps my client library dumb: services speak plain HTTP/UDS to a collector instead of every service in every language carrying a Kafka client and broker creds. The idempotent write sits at the drain, so retries are harmless and I never need exactly-once delivery."

## Connections
- [[system-design-concepts/edge-shed-vs-core-durability]] — the queue is the "durable core" end; it's the counterpart to edge shedding
- [[system-design-concepts/serving-constrained-resources]] — a queue smooths bursts but can't fix a capacity deficit; the shed/degrade/add-capacity fork
- [[system-design-concepts/commutative-aggregation]] — idempotent per-cell writes let the queue run at-least-once, no exactly-once needed
- [[system-design-concepts/exactly-once-semantics]] — why "Kafka gives single delivery" is over-claimed; close correctness at the write, not the queue
- [[system-design-concepts/the-log-abstraction]] — the queue as replayable log + serving split; fan-out to many consumers
- [[tech/kafka]] — partitioning by series key for ordering + parallelism; a log is not a delay-queue
- [[system-design-concepts/hot-key-write-contention]] — partition choice at the queue mirrors the hot-key/partition-key decision

## Sources
- [[sources/docs/design-metrics-counters-mock-interview]] — the "queue front-vs-behind wouldn't make a difference" miss and its correction: guard the bottleneck, keep clients dumb
- [[sources/docs/metrics-collection-observability-self-study]] — remote_write/push buffering and the collector-as-ingress pattern
