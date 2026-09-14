---
source: docs/azure-storage-interview-loop.md
source_url: https://github.com/redblackcoder/interview-prep-raw/blob/master/docs/azure-storage-interview-loop.md
type: doc
date_extracted: 2026-09-14
topic: Interview Prep Wiki
secondary_source: code/keyed-task-executor/ (https://github.com/redblackcoder/interview-prep-raw/blob/master/code/keyed-task-executor/)
---

# Azure Storage (Object Replication) Interview Loop

Interview loop for a senior/architect role on an Azure Storage object-replication team. One session, two parts: (1) a past-experience deep dive on metadata replication + tenant scaling, and (2) a live concurrent-programming exercise (KeyedTaskExecutor). Extracted to seed one concurrency-constructs page, one coding-pattern page, and one behavioral deep-dive page.

## Key Ideas

**Part 1 — Experience (Salesforce Data Cloud metadata replication + 100K-tenant scaling):**
- Two forcing functions: scale metadata platform to 100K tenants (from ~20K in US East), and fix a replication pipeline that fell over on **bulk writes** (sandbox→prod promotion, training-org creation).
- **Cacheability fix:** objects were non-cacheable in Salesforce's core metadata layer; root cause was the **ID scheme** (couldn't encode enough tenants for the cache's rules); fixed by extending the tenant bit-range with the core architect → reads scaled.
- **Batching fix:** replication rode a shared **eventing framework** that throttles partner teams past a CPU/event threshold; bulk writes tripped it; batching cut events + de-duplicated per-event core reads + lowered CPU (trade: longer-lived transactions).
- **Consistency:** ordered, stamped **logical-transaction streaming** (not Oracle CDC) + a **dependency graph** → per-object error reporting instead of whole-batch failure.
- **Idempotency:** replicate **object snapshots, not deltas** → retries re-apply the latest full object; dropped events self-heal on the next write.
- **Fail-safe:** async event/update gap could silently drop events; backstopped by snapshot self-healing + a periodic (expensive) delta-reconciliation job (anti-entropy-shaped).
- **SLA:** ~5 min p99, tied to interactive training-org readiness; later sidestepped the pipeline for known-shape orgs via pre-built S3 packages.

**Part 2 — Coding (KeyedTaskExecutor):** bounded concurrent executor — same-key serial in accepted order, ≤4 concurrent, ≤1000 in flight, reject when full / after shutdown, failure isolation, drain-with-timeout shutdown. Clean design: **per-key serial queue + shared bounded pool + atomic CAS admission + completion-driven hand-off**.

## My Understanding

- The behavioral round went well on substance but was **qualitative where it should have been quantitative** ("got slow", "a lot of time") and **collective where it should have been personal** ("we" across three teams I led). The fix isn't new content — it's pre-loading 3-4 real numbers and claiming the judgment calls as mine.
- On the coding round, my instincts were right (CAS admission, per-key serialization, tracking which keys run) but I reached for a **polling dispatcher**, which is the tell that I hadn't internalized **completion-driven hand-off**: the *finishing* task schedules the key's next task. The other reflexes I need automatic: decrement the counter in `finally`, wrap `run()` in `try/catch`, and never `sleep()` for shutdown — wait on a drain signal.
- The unifying lesson across both parts: **idempotency + failure isolation + bounded admission** are the same three ideas at two altitudes (distributed replication vs in-process executor).

## Open Questions

- For the behavioral story: which exact before/after numbers can be honestly cited (replication p99 before vs the 5-min SLA; event volume before/after batching; read latency at 100K)? — to be filled from real figures before the next loop.
- KeyedTaskExecutor: how would the design change if strict cross-key fairness (not just per-key order) were required? (Sketch: front the pool with an explicit ready-queue.)

## Connections

- Relates to: [[wiki/theory/concurrency-constructs]] — the toolkit the coding problem decomposes into
- Relates to: [[wiki/coding-patterns/keyed-serial-executor]] — the worked problem + complete code
- Relates to: [[wiki/behavioral/project-metadata-replication-scaling]] — the experience story + vague-spots-to-fix
- Builds on: [[wiki/system-design-concepts/exactly-once-semantics]] — snapshot-not-delta idempotency
- Nuances: [[wiki/system-design-concepts/anti-entropy-merkle-trees]] — the periodic delta-reconciliation fail-safe

## Key Quotes / Annotations

- Interviewer, on partial batch failure: *"What if the client retried the whole batch again — would the successful transactions execute again?"* → answered via snapshot-not-delta idempotency.
- Candidate, on the coding problem: *"Concurrent programming is really hard to get."* — the incomplete attempt is exactly why the corrected pattern is worth extracting.
- Interviewer close: *"I got the approach... not possible to implement everything in the short time."* — approach was read as sound; execution/finish was the gap.
</content>
