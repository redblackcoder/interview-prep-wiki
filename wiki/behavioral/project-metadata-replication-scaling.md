# Project Deep-Dive: Metadata Replication + 100K-Tenant Scaling (Data Cloud)

**Prompt fit:** "Tell me about a challenging/complex project," "a system you scaled," "a hard performance problem," "deep technical ownership." The interviewer will *drill*, not just listen — this page is organized so the story survives the drill, and flags **exactly where the live telling went vague** so those spots get tightened.

> Context: Salesforce **Data Cloud** storage & metadata team. Core metadata lives in an Oracle-backed **core** DB (system of truth); it replicates to an **off-core** lakehouse (Iceberg) metadata repository that serves queries/ingestion.

## Situation & stakes (lead with this)
Two forcing functions hit at once:
1. **Scale the metadata platform to 100K tenants** in the biggest region (US East), up from ~20K.
2. **The metadata-replication pipeline was too slow** — customer escalations, worst on **bulk writes**: sandbox→production promotion and training-org creation, where thousands of entities land at once.

Why it mattered: replication lag was tied to an **interactive** experience — customers (and internal folks provisioning training orgs) waited on a "your org is ready" email gated by replication finishing. Slow replication = people blocked for hours. Internal SLA: ~5 min p99.

## Task
Lead the effort across **three scrum teams** (one core-side, two lakehouse/off-core) to (a) make reads scale to 100K tenants and (b) bring bulk-write replication back under SLA.

## Actions (the two changes that mattered)
- **Made core objects cacheable.** Their object modeling made entities non-cacheable in Salesforce's core metadata layer, so 100K-tenant read tests degraded. Root cause was the **ID scheme** — it couldn't encode enough tenants to satisfy the cache's rules. Worked with the core platform architect to **extend the tenant bit-range**, which made objects cacheable and dropped read latency under the perf team's emulation.
- **Batched the replication events.** Replication rode Salesforce's shared **eventing framework**, which throttles any partner team that trips a CPU/event telemetry threshold (deprioritizes and drains their events slower). Bulk writes tripped it. **Batching** cut event count, de-duplicated the per-event reads from core (reads dropped roughly by the batch size), and lowered CPU so we stopped tripping the throttle — at the cost of **longer-lived transactions** on the write side, which needed their own tuning.

Supporting depth (came out under follow-ups):
- **Batch consistency under partial failure:** ordered **logical-transaction streaming** (stamped for order, not Oracle CDC) cut out-of-order-apply retries; a **dependency graph** among logical entities let us return **per-object errors** instead of failing the whole batch.
- **Idempotency:** replicate **snapshots of the object, not deltas** → any retry re-applies the latest full object; safe by construction.
- **Multi-tenant fairness:** reused the eventing framework's tenant-awareness (throttle a noisy tenant) rather than rebuilding it off-core.
- **Drop detection:** event-creation and object-update weren't in one transaction, so events could be silently lost; snapshots self-heal on the next update, plus a **periodic delta reconciliation** job (anti-entropy-shaped, expensive) as a fail-safe.
- **Later optimization:** for known-shape training orgs, **pre-built the metadata package in S3** and just triggered materialization off-core — sidestepping the streaming pipeline for the bulk case entirely.

## Result
Bulk-write throttling stopped recurring; reads scaled under 100K-tenant emulation; interactive provisioning stayed under the ~5-min SLA. *(Attach real numbers — see below.)*

---

## Where it sounded vague — and how to fix it

The single biggest gap: **the story is qualitative where it should be quantitative, and collective where it should be personal.** Concrete fixes:

1. **No before/after numbers.** Phrases used live: "got slow," "took a lot of time," "reduced by the batch size." At the architect bar this reads as hand-wavy.
   - **Fix — pre-load 3-4 hard numbers and say them:** replication p99 *before* vs the 5-min SLA *after*; event volume per bulk op before/after batching (e.g. "~N events → ~N/50"); CPU/throttle-trip frequency before/after; read latency at 100K tenants before/after caching. Even honest approximations ("roughly an order of magnitude fewer events") beat "a lot."

2. **"I" vs "we" is blurred.** You *led three teams* but narrate in collective "we did X." Interviewers can't score what they can't attribute to you.
   - **Fix — name 2-3 decisions that were yours:** "*I* diagnosed the non-cacheability root cause down to the ID scheme," "*I* made the call to batch rather than shard the pipeline," "*I* drove the cross-team agreement with the core architect to change a shared ID format." Keep "we" for execution, claim the judgment calls.

3. **The ID-scheme / cacheability story rambled before landing.** Live, it wandered through "Salesforce metadata is old, lots of layers…" before reaching the point.
   - **Fix — compress to a 4-beat arc:** *problem* (objects weren't cacheable → reads degraded at scale) → *root cause* (ID scheme couldn't encode enough tenants for the cache's rules) → *what I drove* (extended the tenant bit-range with the core architect, since we were their largest consumer) → *result* (cacheable, read latency dropped under 100K test).

4. **"The perf team emulated 100K" needs a cleaner ownership line.** Honest that you didn't see real 100K load — good — but state your role in the loop.
   - **Fix:** "I owned the changes; the perf team ran the 100K emulation and *I* used their results to find the next bottleneck each iteration." Turns a caveat into a method.

5. **Batching's downside was mentioned but not owned as a trade-off.** Longer transactions were noted almost apologetically.
   - **Fix — frame it as a deliberate trade you managed:** "Batching trades more events for longer-held transactions; I accepted that and tuned the write path (…) because the throttle was the dominant cost." Shows you reason about trade-offs, not just wins.

6. **Drop detection sounded like an admission of a flaw.** The async event/update gap losing metadata came across as "our sync sometimes dropped things."
   - **Fix — lead with the design property that made it safe:** "Because we replicate snapshots, not deltas, a lost event is self-healing — the next update carries the full object — and a periodic reconciliation job is the backstop. The gap was known and bounded, not silent data loss." (Ties to [[system-design-concepts/anti-entropy-merkle-trees]].)

## Crisp reusable soundbites (rehearse these)
- **One-liner open (~30s, ~90 words):** "We had two pressures at once: scale the metadata platform to 100K tenants and fix a replication pipeline that fell over on bulk writes. I led it across three teams. Reads didn't scale because our objects weren't cacheable — I traced that to the tenant ID scheme and drove a change to the core format with the platform architect. Writes fell over because our per-event work tripped a shared eventing throttle on bulk loads — I batched the replication to cut events and CPU. Result: back under the 5-minute SLA and scaling in perf tests."
- **Idempotency (one line):** "We replicate object snapshots, not deltas, so retries are safe by construction and dropped events self-heal on the next write."
- **Consistency (one line):** "Ordered, stamped logical transactions plus a dependency graph, so a partial failure returns per-object errors instead of failing the whole batch."

## Q&A the interviewer will probe
- **"How did you keep the batch consistent if some ops failed?"** → ordered stamped streaming + dependency graph → per-object error reporting; whole batch doesn't fail.
- **"How is it idempotent on retry?"** → snapshot-not-delta; retry re-applies the latest full object.
- **"How do you protect one tenant from a noisy neighbor?"** → the core eventing framework is tenant-aware and throttles the noisy tenant; off-core work is driven only by core changes, so no separate fairness layer needed there.
- **"What if you drop an event?"** → self-healing snapshots + periodic reconciliation backstop; bounded staleness, not silent loss.
- **"Why batch instead of shard/parallelize harder?"** → the bottleneck was a *shared throttle keyed on our aggregate CPU/event footprint*; more parallelism would trip it faster. Batching attacked the actual cost. *(Have this ready — it's the "why this fix" question.)*

## Self-coaching (staff+ delivery)
- **Lead with the constraint, not the tech.** The 5-min interactive SLA is *why* everything else matters — state it first so the listener knows the stakes.
- **Numbers, ownership, then narrative.** Every beat should have a metric and an "I."
- **Turn caveats into method** (perf-team emulation) and **flaws into design properties** (snapshot self-healing).
- **Steelman the constraints you worked within** (shared eventing framework existed for fairness across partner teams) — shows you optimized within a real org, not in a vacuum.

## Connections
- [[behavioral/disagreement-customer-proxy-connectivity]] — sibling story from the same tenure (Data Cloud / Zero-Copy); pick that for disagreement/influence prompts, this one for scaling/complex-project prompts
- [[system-design-concepts/exactly-once-semantics]] — snapshot-not-delta replication is idempotency-by-construction; the effectively-once framing
- [[system-design-concepts/anti-entropy-merkle-trees]] — the periodic delta-reconciliation fail-safe is anti-entropy; name it as such to sound precise
- [[system-design-concepts/the-log-abstraction]] — ordered stamped logical transactions are a log/CDC-style change stream driving a downstream projection
- [[theory/consistency-models]] — the async core→off-core gap is read-your-writes/staleness; the vocabulary to describe the "I updated schema but my query failed" escalations

## Sources
- [[sources/docs/azure-storage-interview-loop]] — Part 1: the experience deep dive and the moments flagged as vague
- Personal experience — Salesforce Data Cloud storage & metadata team (details generalized; specifics retained privately).
</content>
