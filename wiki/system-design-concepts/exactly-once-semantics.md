# Exactly-Once Semantics (Effectively-Once)

"Exactly-once **delivery**" over an unreliable network is impossible (two-generals). What real systems provide is exactly-once **processing / effect** — achieved by the recipe **at-least-once delivery + idempotent dedup + (for multi-step flows) atomic commit**. This is the correctness backbone of billing, ledgers, and any counter that must reconcile exactly.

## The one-sentence mental model

> **You can't deliver exactly once, so deliver *at least* once and make the duplicate a no-op: dedup by a stable key at the sink. "Exactly-once" is really "at-least-once + dedup" wearing a nicer name.**

## How it works

Three cooperating pieces (framed via Kafka, but the pattern is general):

### 1. Idempotent producer — kills producer-retry duplicates
Each producer gets a **Producer ID (PID)**; every record carries `(PID, partition, sequence#)` with a monotonic per-partition sequence. The broker remembers the last sequence per `(PID, partition)` and **drops** an already-seen sequence and **rejects** an out-of-order one. A resend after a lost ACK doesn't double-append. → This is *dedup by a key at the sink*, the exact pattern for the tagged-counter.

### 2. Atomic commit — the consumer-crash half *(deferred detail)*
The dangerous window: if a consumer commits its input offset **before** its output write lands → crash = **loss**; **after** → **duplicate**. Transactions bind the output writes and the input-offset commit into one atomic unit so neither gap exists. *(Kafka's transaction machinery / `sendOffsetsToTransaction` — intentionally left as a pointer here; see Open Questions in the source extract.)*

### 3. Read-committed isolation
Consumers must ignore uncommitted/aborted output to actually observe once-only results.

### The boundary that trips people up
Exactly-once holds **Kafka → process → Kafka** (a closed, replayable system). It does **not** automatically extend to **external side effects** — a DB write, an API call, an email. Those need either an **idempotent sink** (dedup by a business key) or a sink that enlists in the transaction. "Send the termination email" is the canonical non-idempotent external effect that this does *not* cover for free.

## Key points
- **Delivery vs effect:** correct the premise first — the guarantee is on the *effect/processing*, not the wire delivery.
- **Dedup needs a stable idempotency key** and a window over which you remember it. Exact dedup over an unbounded window is expensive online — which is why, for billing, you push it to a **batch recompute over the immutable log** (`count(distinct id)`), where exactness is free and reproducible. See [[system-design-concepts/lambda-vs-kappa]].
- Approximate consumers (dashboards) can dedup **probabilistically** (Bloom/Cuckoo filter over a bounded window) — different correctness model, cheaper mechanism. See [[theory/bloom-filters]].
- The recipe generalizes far beyond Kafka: payment processors, at-least-once webhooks with an `Idempotency-Key`, and any "retry-safe" API all use it.
- Two-generals reminder: this is *why* you can never get true exactly-once delivery — hence the "make the duplicate harmless" strategy.

## Interview angle

> "First I'd correct the phrase — you can't do exactly-once *delivery*, that's two-generals. What you build is exactly-once *effect*: deliver at least once and make duplicates no-ops by deduping on a stable key at the sink. Concretely in Kafka: an idempotent producer tags records with a producer-id and per-partition sequence, and the broker drops duplicates — that's dedup-by-key. For multi-step consume-process-produce you make the output write and the input-offset commit atomic so a crash can't lose or double anything. And critically it only holds Kafka-to-Kafka: an external DB write or an email still has to be idempotent on its own. For an exact monthly total I wouldn't even keep a giant online dedup set — I'd recompute distinct-by-id over the immutable event log, where exactness is a batch property."

## Connections
- [[system-design-concepts/lambda-vs-kappa]] — exact = recompute-over-immutable-log; the batch/replay path *is* the exactness mechanism
- [[system-design-concepts/event-time-vs-processing-time]] — the other half of "no silent loss": event-time correctness + checkpointed offsets
- [[tech/kafka]] — idempotent producer, transactions, read_committed are Kafka's implementation
- [[theory/bloom-filters]] — the cheap probabilistic dedup for the approximate path
- [[theory/durability-rpo-rto]] — the non-idempotent-replay hazard on crash recovery is the same danger this addresses
- [[system-design-concepts/distributed-id-generation]] — a good idempotency key is often the unique ID minted here

## Sources
- [[sources/docs/kafka-101-bytebytego]] — Kafka Streams exactly-once, the producer/consumer model
- [kafka-101-bytebytego.md](https://github.com/redblackcoder/interview-prep-raw/blob/master/docs/kafka-101-bytebytego.md) — Stanislav Kozlovski / ByteByteGo
- Conversation debrief (tagged-counter mock) — "exact dedup is a batch property; at-least-once + dedup = effectively once"
