---
source: docs/design-metrics-counters-mock-interview/
source_url: https://github.com/redblackcoder/interview-prep-raw/blob/master/docs/design-metrics-counters-mock-interview/
type: doc
date_extracted: 2026-09-09
topic: system-design-concepts
---

# Design a Counters / Metrics Pipeline — Mock Interview + Graded Review (#9)

Distilled from a **Stripe developer-productivity** ~45-min systems-design screen: **"a `track.increment(metric)` library ships with every service; design everything after the call — how the data is sent, collected, stored, and queried."** Restricted to **counters** (no latency/percentiles). Feed/alerting/visualization were out of scope, so the round funneled onto **the ingest pipeline: aggregation placement, agent topology, transport semantics, and where a queue lives.** Graded against the same rubric as every other round — the **Competency matrix** in [[private/staff-swe-readiness-report]]. Transcript: [design-metrics-counters-mock-interview/](https://github.com/redblackcoder/interview-prep-raw/blob/master/docs/design-metrics-counters-mock-interview/Transcript.md).

**Verdict: lean hire / borderline, ~6.5/10 at the Staff bar. Not a clean Staff pass.** Same shape as the corpus: a real at-bar *ceiling* (storage-engine reasoning, and the edge load-shedding generalization — "this is the opposite of a queue") with both standing *gates* firing — **no quantification at all (G1, here as total omission)** and **the one correctness seam (dedup/double-count) over-claimed via Kafka instead of closed with an idempotent write (G2)**. **G3 did not recur** — stayed inside the transport/aggregation crux — though much of the depth was interviewer-pulled. The interviewer was **high-signal** (drove specific trade-off distinctions, gave targeted nudges), the opposite of #7, so the two gaps are real signal, not low-information noise.

## Scorecard (this round vs. corpus "Now" vs. Bar)

| Competency | **#9 Metrics** | "Now" | Bar | Note |
| --- | :--: | :--: | :--: | --- |
| Problem framing & scoping | **8** | 8 | 8 | Restricted to counters *with reason*; named both NFRs (minutes-freshness, lossy-tolerant) and derived "lossy ⇒ design freedom." Textbook S1. |
| Requirements & NFR derivation | **7** | 7.5 | 8 | Event-time-vs-processing-time surfaced unprompted (strong). Never turned volume/cardinality/query-load into a *requirement* — stayed qualitative. |
| **Quantitative / estimation** | **4** | 5.5 | 8 | **G1 recurred as a no-show.** Zero QPS / write-volume / storage / cardinality math; every scaling call ("partition the queue," "shard the DB") ungrounded. Didn't derail the design, but absent at the bar. |
| High-level architecture | **7.5** | 7.5 | 8 | Clean lib→agent→collector→(queue)→TSDB→read-replicas. Storage-first reasoning (LSM vs B-tree; TSDB vs Cassandra vs RDBMS) was genuine tool-fit depth. |
| **Deep-dive correctness-closure** | **5.5** | 5.5 | 9 | **G2 recurred, textbook.** Detected double-counting on retry, then over-claimed "Kafka message-id + consumer group ⇒ single delivery." Never reached the real close: idempotent overwrite keyed by `(metric, source, window-start)`. |
| Depth on the true crux | **6.5** | 6 | 8 | **G3 did NOT recur** — sat in the transport/aggregation crux, produced the edge load-shedding insight and generalized it. Caveat: daemonset / queue-placement / UDP were all *interviewer* prompts. |
| Tradeoffs & intellectual honesty | **7.5** | 8.5 | 8 | Usual spine, slightly below par. Honest "I don't know"s (sidecar vs daemonset, Cassandra caching) — good honesty on core facts. "Queue front-vs-behind wouldn't make a difference" was a miss the interviewer corrected. |
| Failure modes / ops / rollout | **6.5** | 7 | 7 | Retries, collector-down, backpressure, DB-can't-keep-up, sharding, read replicas — reasonable. Missed the metric-specific ones: **cardinality explosion**, and (ironically) **agent versioning/rollout**. |
| Communication & interview craft | **7** | 7.5 | 8 | Good storage-first structure; integrated every nudge gracefully (S4). Some non-converging loops (circled dedup, left it on Kafka). |

**Net:** consistent with the standing thesis — *ceiling is real; the gate is floor + last-hop closure.* This is a **real, high-signal** round (unlike self-graded #8 and low-signal #7), so it corroborates that one good day (#6) never retired G1/G2. The **Now** column is left unchanged — one round of corroborating evidence for an already-flagged pattern doesn't move the considered judgment — but G1 gains a **new failure mode (omission, not slip)** and G5 gains a clean high-signal contrast to #7.

## Key ideas (and where the design is right vs. must close)

- **K0 — The crux is a fan-in reduction across a *deliberately lossy* pipeline, and the organizing principle is "reliability increases as you move inward."** The strongest conceptual moment: when the interviewer asked about UDP at the app→agent hop, the candidate said *"this is kind of the opposite of having a queue"* and tied it to the SLA — the data is operational, not billing, so **shedding at the edge is correct**, while **buffering/persisting in the core** (queue in front of the DB) is also correct. That gradient — shed where volume is highest and value-per-event lowest, persist where each message already represents thousands of aggregated events — is the at-or-above-bar idea of the round. It was reached *reactively* (prompted), not volunteered up front; stating it as the framing first would have turned the whole round. → [[wiki/system-design-concepts/edge-shed-vs-core-durability]].

- **K1 — Storage-engine reasoning was a real at-bar deep dive.** Correctly rejected a B-tree RDBMS for the write path (append-heavy, index-rebalance cost), reached for **LSM-tree**-backed stores, and reasoned about **TSDB vs Cassandra vs relational** on the right axes (write amplification, recent-window reads served from the in-memory memtable, compaction). Honest about the edge of his knowledge ("not sure how Cassandra caches recent points"). This is the clearest evidence the ceiling is real. → [[wiki/system-design-concepts/rds-vs-key-value-store]], [[wiki/system-design-concepts/hash-vs-range-partitioning]].

- **K2 — Sidecar vs DaemonSet: right final lean, incomplete reasoning.** Got to "run the agent as a separate process so its memory/bugs don't touch the service," and — *with a nudge* — the **resource-multiplication** axis (N sidecars per node vs 1 daemon). But he reasoned almost entirely about runtime memory and initially said "it doesn't make a difference," missing the two axes that actually decide it for a shipping agent: **(1) central upgradeability** — a sidecar's version is pinned to the app deploy (ship an agent fix ⇒ redeploy every service or rely on webhook re-injection; teams pin old versions), whereas a DaemonSet is upgraded by the platform team independently; this is why real agents (Datadog, otel-collector agent mode, fluent-bit, node_exporter) are DaemonSets. **(2) shared-dependency blast radius** — a DaemonSet mixes tenants, so one flooding pod degrades everyone on the node ⇒ needs per-source fairness; a sidecar is naturally isolated. → [[wiki/system-design-concepts/sidecar-vs-daemonset]].

- **K3 — Local IPC transport: the UDP/load-shed insight (round's best), with the axis slightly under-named.** He noticed UDP would *drop* under a tight-loop `track.increment` bug while TCP would back up and OOM the agent — correct and valuable. The sharper framing he didn't quite state: the real axis is **backpressure vs load-shedding**, and a **Unix domain socket lets you choose** (`SOCK_STREAM` = reliable + backpressure into the app; `SOCK_DGRAM` = message framing, drop-on-full). For a metrics path that must never harm the app you want **fire-and-forget datagrams** (UDP loopback for a DaemonSet via host IP, or datagram UDS for a sidecar via shared volume), non-blocking, drop-on-full, with client-side batching. HTTP's request/response couples app latency to the agent and is the wrong tool on the hot path (reserve it for agent→collector). → [[wiki/system-design-concepts/local-ipc-transports]].

- **K4 — The dedup / double-count seam (G2, highest-priority gap).** He *detected* that an agent retry to a different collector double-counts (good), then **closed it by over-claiming**: "Kafka message-id + consumer-group gives single delivery." That guarantee is overstated — Kafka's idempotent producer dedups only within a producer session on one partition (producer-id + sequence), **not** across different agents/sessions, and consumer groups don't give exactly-once *to the DB*. The clean close he never reached: make the **write idempotent** — each `(metric, source, window-start)` maps to a fixed cell and a retry **overwrites** rather than adds, so duplicates are harmless and you need no exactly-once delivery at all. This is the [[wiki/system-design-concepts/commutative-aggregation]] move (counters are idempotent per cell) and is exactly the *detection ≠ closure* pattern the report tracks as G2. → [[wiki/system-design-concepts/exactly-once-semantics]].

- **K5 — Queue vs direct, and front-vs-behind the collector: the "wouldn't make a difference" miss.** On *whether* to add a queue he had the right instincts (burst absorption, DB protection, fan-out) but didn't note that the agents' 1s batching **is already a distributed buffer**, so the queue's marginal value is specifically **DB-outage durability + multi-consumer fan-out + smoothing to the bottleneck**, not "buffering." On *placement* he said it "wouldn't make a difference" — the real answer: **the queue's job is to protect the bottleneck (the DB), so it belongs in front of the bottleneck — i.e. behind a thin collector**, not in front of the collector. Queue-in-front forces a Kafka client into every service in every language (bad for "a library that ships with everything"); a thin collector shields clients with a dumb protocol and terminates into the queue as the internal backbone. → [[wiki/system-design-concepts/queue-placement]].

- **K6 — Estimation was absent (G1, new manifestation).** Not a wrong number — *no* numbers. No services×metrics×emit-rate → writes/s, no storage sizing, no cardinality, no quantification of what 1s pre-aggregation actually saves. Every scaling decision was qualitative. At Staff you volunteer the back-of-envelope even unprompted; the interviewer never asked and the candidate never offered. One sentence — *"N services × M metrics × emit-rate → writes/s → does one TSDB shard hold it?"* — would have grounded the entire scaling discussion.

- **K7 — Event-time vs processing-time: an at-bar half of the correctness deep-dive.** Chose to stamp the **event's timestamp in the payload** (not arrival time) and reasoned correctly about late-arriving points reshaping an already-observed window, and about clock-skew keeping the answer accurate to ~tens of ms. Solid, unprompted. → [[wiki/system-design-concepts/event-time-vs-processing-time]].

## Growth-axis check (vs. the readiness report)

- **G1 (estimation) — RECURRED, new form: total omission.** Prior rounds slipped by 10× (ChatGPT, Uber); here estimation simply did not happen on a real, high-signal round. Three of the last four rounds now score ≤5. Still the top gate. Fix unchanged: memorize the primitives so a sizing sentence is *free*, and volunteer it unprompted.
- **G2 (detection ≠ closure / self-audit) — RECURRED, textbook.** Spotted the double-count, asserted a guarantee Kafka doesn't give, never reached idempotent-overwrite-by-window-key. Same family as the auction linearizability over-claim and the Uber Bloom-on-correctness-path. The self-audit reflex ("what does this operation's algebra actually need?") is the exact fix — counters are idempotent, so you need durability + a fixed cell, not exactly-once.
- **G3 (comfort-zone drift) — did NOT recur.** Third largely drift-free round (#6, #8, #9): sat in the transport/aggregation crux instead of fleeing to an easier neighbor. Discount slightly — the interviewer *steered* most of the depth (daemonset, queue placement, UDP).
- **G4 (dropped threads) — minor recurrence.** Dedup was raised, circled, and parked on "Kafka handles it." Cardinality never came up.
- **G5 (interviewer calibration) — HIGH-signal room, the anti-#7.** Probed every trade-off, drove specific distinctions, gave targeted nudges that would touch the actual design. Weight this round heavily: the two gaps are real signal.

## Interview-craft lessons

- **Lead with the organizing principle, don't reach it reactively.** The "reliability increases inward / shed at edge, persist at core" gradient (K0) was the best idea in the room and it came out *after* a UDP prompt. Said first, it *derives* UDP-at-edge, idempotent-writes, and queue-in-front-of-DB as consequences — that's the difference between answering questions and framing the problem.
- **Close the one correctness seam before widening.** The moment you introduce a retry across stateless collectors, narrate the last hop: "duplicates are possible here; I make them harmless with an idempotent write keyed by `(metric, source, window)`." Don't outsource it to a mislabeled Kafka guarantee.
- **Volunteer one sizing sentence, always.** Even a rough writes/s and a "so I need ~K shards" grounds every downstream decision and retires the round's single biggest recurring gate.
- **"It doesn't make a difference" is almost never the answer to a placement question.** When you can't see the difference, ask *what is this component's one job?* — the queue's job is protecting the bottleneck, which locates it immediately.

## Connections
- Graded against, and feeds: [[private/staff-swe-readiness-report]] — Competency matrix + G1/G2/G3/G5 growth axes; #9 is the high-signal real-round counterpart to #7.
- The organizing principle: [[wiki/system-design-concepts/edge-shed-vs-core-durability]] — shed at the edge, persist in the core; where the UDP-vs-queue tension resolves.
- Agent topology: [[wiki/system-design-concepts/sidecar-vs-daemonset]] — upgradeability + blast-radius, not just runtime memory.
- The app→agent hop: [[wiki/system-design-concepts/local-ipc-transports]] — HTTP loopback vs UDS vs UDP; backpressure vs load-shedding.
- Queue vs direct, front vs behind: [[wiki/system-design-concepts/queue-placement]] — the queue guards the bottleneck.
- The correctness close (K4): [[wiki/system-design-concepts/commutative-aggregation]] (idempotent per-cell counters) + [[wiki/system-design-concepts/exactly-once-semantics]] (why at-least-once + idempotent write beats exactly-once delivery).
- Time semantics (K7): [[wiki/system-design-concepts/event-time-vs-processing-time]] — payload time, late-arrival window mutation.
- Storage (K1): [[wiki/system-design-concepts/rds-vs-key-value-store]], [[wiki/system-design-concepts/hash-vs-range-partitioning]]; recent-window reads served from the memtable.
- Adjacent metrics study (same problem family, complementary): [[wiki/system-design-concepts/metrics-pull-vs-push]], [[wiki/system-design-concepts/red-metrics-exposition]], [[wiki/system-design-concepts/mergeable-metrics-and-quantiles]], [[wiki/system-design-concepts/counter-reset-and-restart-recovery]].
- Grounding the sizing sentence (G1): [[wiki/theory/latency-numbers]].

## Sources
- [[sources/docs/metrics-collection-observability-self-study]] — the self-study that pre-loaded the pull/push, RED, mergeable-quantile, and restart-recovery background this round drew on.
- [design-metrics-counters-mock-interview/](https://github.com/redblackcoder/interview-prep-raw/blob/master/docs/design-metrics-counters-mock-interview/Transcript.md) — full mock-interview transcript (Stripe dev-productivity screen).

## Concepts flagged for wiki (created with this extract)
- **Edge-shed vs core-durability** — reliability increases inward; the UDP-vs-queue gradient. *(new: [[wiki/system-design-concepts/edge-shed-vs-core-durability]])*
- **Sidecar vs DaemonSet** — upgradeability + blast-radius decide it, not runtime memory. *(new: [[wiki/system-design-concepts/sidecar-vs-daemonset]])*
- **Local IPC transports** — HTTP loopback vs UDS vs UDP; backpressure vs load-shedding. *(new: [[wiki/system-design-concepts/local-ipc-transports]])*
- **Queue placement** — queue vs direct, and in-front-of vs behind the collector; guard the bottleneck. *(new: [[wiki/system-design-concepts/queue-placement]])*
