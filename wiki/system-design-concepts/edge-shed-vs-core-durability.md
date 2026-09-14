# Edge-Shed vs Core-Durability (Reliability Increases Inward)

A design lens for any telemetry / event pipeline: **reliability should not be uniform — it should increase as you move from the edge toward the core.** At the edge, event volume is highest and the value of any single event is lowest, so you **shed** (drop under pressure, fire-and-forget). By the core, events have been aggregated — each message now stands for thousands of originals — so you **persist** (durable queue, idempotent writes). Spending the same reliability everywhere is the mistake: full durability at the edge melts the app; best-effort at the core loses data that was expensive to compute.

## The one-sentence mental model

> **Shed where volume is high and each event is cheap; persist where you've aggregated and each message is precious. A lossy UDP hop at the edge and a durable queue in front of the DB are the *same* design decision applied at two ends of the value gradient — not a contradiction.**

## Why the gradient exists

Two quantities move in opposite directions along the pipeline:

- **Event rate falls** at every aggregation step: per-process 1s pre-aggregation turns millions of `increment()` calls into one number; a node agent folds across pods; a collector folds across nodes.
- **Value-per-message rises** for the same reason: dropping one raw `increment` loses ~nothing (the SLA is "spot big moves," not billing); dropping one *aggregated* window that represents 50k events loses a visible dashboard point.

So the cost/benefit of reliability inverts end to end. The right pipeline is **lossy-cheap at ingress, durable-expensive at the bottleneck.**

```
 app → [agent]  → [collector] → [queue] → [TSDB]
  ▲ highest rate            lowest rate ▲
  ▲ cheapest event          most valuable message ▲
  SHED (UDP, drop-on-full)   PERSIST (durable log, idempotent write)
```

## The two ends, concretely

- **Edge = load-shedding.** The app→agent hop is fire-and-forget: UDP loopback or a non-blocking datagram UDS, drop-on-full, so a runaway `track.increment` loop can never back-pressure or OOM the real workload. Losing samples here is acceptable *by the SLA*. See [[system-design-concepts/local-ipc-transports]].
- **Core = durability.** In front of the bottleneck (the TSDB), a durable queue absorbs bursts, survives DB outages, smooths writes to the DB's sustainable rate, and fans out to multiple consumers. See [[system-design-concepts/queue-placement]].

The interview tell (from #9): a candidate asked about UDP said *"this is the opposite of having a queue"* — correct, and the deeper point is that **both are right, at different depths.** Stating the gradient up front *derives* UDP-at-edge and queue-at-core as consequences.

## When the gradient flips (know the boundary)

The whole lens assumes **lossy-tolerant** data. If the pipeline carries something order- or exactness-sensitive — billing meters, rate-limit counters that gate real decisions, audit events — you **cannot shed at the edge**; you push durability all the way out (local WAL, at-least-once, backpressure) and pay the app-latency cost. The first question is therefore always *"what does a dropped event actually cost?"* — the SLA, not reflex, sets where on the gradient shedding is allowed.

## Key points
- **Reliability is a dial per hop, not a global setting.** Set it from the value-per-message at that hop.
- **Aggregation is what earns durability.** You persist in the core precisely because pre-aggregation made each message represent many events — cheap to store, expensive to lose.
- **Edge-shed and core-persist are complementary, not opposed.** They're the same decision (match reliability to value) read at two ends.
- **The flip condition is exactness/ordering sensitivity**, not scale. High volume alone doesn't justify shedding; low value-per-event does.
- **Idempotence lets the core be cheap too:** an idempotent write ([[system-design-concepts/commutative-aggregation]]) means the durable hop needs only at-least-once, not exactly-once.

## Interview angle

> "I'd set reliability as a gradient, not a constant. At the edge — the `increment()` call into the agent — volume is huge and any single sample is worth nothing against a 'spot-big-moves' SLA, so I make that hop lossy: fire-and-forget UDP, drop-on-full, so a bad loop can't hurt the app. As data moves inward it's aggregated — one message now represents thousands of events — so by the time I'm in front of the database I want the opposite: a durable queue that absorbs bursts, survives a DB outage, and rate-limits writes to the bottleneck. UDP-at-edge and queue-at-core look contradictory but they're the same rule — match reliability to value-per-message. The one thing that flips it is if the data is billing or gates a decision; then I can't shed anywhere and pay for durability end to end."

## Connections
- [[system-design-concepts/local-ipc-transports]] — the edge end: fire-and-forget datagrams, drop-on-full, backpressure vs load-shedding
- [[system-design-concepts/queue-placement]] — the core end: a durable queue in front of the bottleneck DB
- [[system-design-concepts/serving-constrained-resources]] — the same "shed / degrade / add-capacity" reflex when a queue can't fix a *deficit*; here we shed by design
- [[system-design-concepts/commutative-aggregation]] — idempotent aggregates make the durable core hop safe with only at-least-once delivery
- [[system-design-concepts/hot-key-write-contention]] — aggregate-and-fold is the same move that both reduces edge volume and dissolves core contention
- [[system-design-concepts/metrics-pull-vs-push]] — push pipelines choose where sampling/shedding happens; pull sheds implicitly by scrape interval
- [[system-design-concepts/event-time-vs-processing-time]] — shedding at the edge is a *completeness* trade you accept because the SLA tolerates it

## Sources
- [[sources/docs/design-metrics-counters-mock-interview]] — "this is the opposite of a queue"; deriving edge-shed vs core-durability from a lossy-tolerant SLA
- [[sources/docs/metrics-collection-observability-self-study]] — sampling, UDP exposition, and the lossy-vs-exact split that underpins the gradient
