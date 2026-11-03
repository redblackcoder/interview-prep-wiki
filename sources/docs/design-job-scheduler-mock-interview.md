---
source: interview session (raw transcript not published)
type: doc
date_extracted: 2026-09
date_revised: 2026-09
topic: system-design-concepts
---

# Design a Distributed Job Scheduler — Mock Interview

Distilled from a Senior Staff-level ~20-min mock systems-design round: **"design a multi-tenant cron + ad-hoc job scheduler — 10k jobs/s, exactly-once, start within 1 s of schedule, highly available, with history."** Format was a **pre-prepared design presented, then grilled**. The round funneled onto **the dispatch claim (exactly-once), capacity, and the thundering herd.**

## Key Ideas

- **The crux is a lease-based state machine with one conditional write per transition.**
  `UPDATE executions SET state='CLAIMED', owner=:dispatcher, fence=fence+1, lease_until=now()+30s WHERE id=:x AND state='QUEUED'` — rows-affected = 1 wins, 0 means someone else did. **Claim before submit, never after.** The driver receives the fence token; every later transition (`RUNNING`, heartbeat, terminal) is `WHERE id=:x AND fence=:f`, so a stale driver's writes fail. Heartbeats *are* lease renewals; a driver that can't renew **kills its own job before `lease_until`**, and retry is allowed only after lease expiry + a skew margin — overlap becomes bounded, not open-ended. Once the claim is a CAS, Redis duplicates, dispatcher retries, and reconciler re-pushes are all harmless. → [[wiki/system-design-concepts/lease-and-fencing]], [[wiki/system-design-concepts/exactly-once-semantics]].

- **Architecture: scheduler / dispatcher / driver layering, with the driver as a trust boundary.** Splitting control plane (driver: trusted, holds DB creds, reports status) from data plane (sandboxed worker running arbitrary binaries) is exactly Borg/k8s' kubelet-vs-container split. Two-level placement (dispatcher → pool, driver → worker) mirrors two-level schedulers (Mesos) and cuts dispatcher metadata. Pre-dispatch with start delay and poll-interval < lookahead-window together protect the start SLA. → [[wiki/system-design-concepts/distributed-job-scheduler]], [[wiki/system-design-concepts/agent-tool-sandboxing]].

- **Don't promise exactly-once.** Say it up front: *"exactly-once is impossible for arbitrary side effects. I guarantee at-most-one lease-holding attempt at a time, and at-least-once execution; I hand the job a stable `execution_id` + `attempt` as an idempotency key; and I offer an at-most-once mode for jobs that must never double-run."* That turns a concession into a product decision. → [[wiki/system-design-concepts/exactly-once-semantics]].

- **Capacity.** With the requirement as stated (10k starts/s):
  - Concurrency = 10k starts/s × 600 s = **6M running** (Little's law).
  - Heartbeats @ 30 s = 6M / 30 = **200k writes/s** — dominates; why heartbeats can't sit on Postgres as-is.
  - Transitions ≈ 4 per execution (insert, claim, running, terminal) × 10k = **40k writes/s** → ~8–10 shards at ~5k each, before a peak factor.
  - **Batch heartbeats per driver** — one renewal carrying N leases turns 200k/s into (number of drivers)/30, trivial, and even keeps leases in the main DB.
  - IOPS ≠ QPS — say which one you're sizing.
  → [[wiki/theory/littles-law]].

- **DynamoDB: right to move heartbeats, wrong to move the state machine.** Heartbeats/telemetry are high-volume noise (KV / time-series is fine). State transitions need the claim CAS, so they must live with the execution row. Consistent alternative: put the *whole* execution table in DynamoDB with conditional writes. "Split and reconcile on read" is where the bugs live.

- **Thundering herd.** Spreading schedules is legitimate (Jenkins `H`, jitter windows). Also: **cron load is known 5 minutes ahead** — the scheduler already materialized it — so **pre-scale / pre-warm pools before 00:00**. Plus: SLA as **p99 by priority class** (priority is quota-bound) and admission control for the rest.

- **Redis durability.** A watermark detects tail loss, not holes. The close: **DB is truth, Redis is a latency hint.** A sweeper (`state='QUEUED' AND scheduled_at < now()+10s`) re-pushes every few seconds; with the CAS claim, over-pushing is free, so Redis loss costs latency, never correctness. → [[wiki/system-design-concepts/transactional-outbox]].

- **Execute-now.** DB insert first (same outbox), then nudge the dispatcher. Writing straight to Redis loses a job you returned 200 for on failover. Per-tenant rate limit; 429.

- **Mutation after materialization (30-second answer).** Executions carry the `definition_version` they were built from; the claim CAS also checks `definition.enabled AND version = :v`, so a paused/deleted definition fails the claim → `CANCELLED`. Deletes are tombstones. One extra predicate at claim time.

- **Hot tenant.** Sharding by job ID spreads CPU but costs tenant reads (scatter-gather on the must-always-work status API), tenant ops (quota, pause, delete become cross-shard), and blast-radius isolation. The real problem is **fairness over worker capacity** — per-tenant quotas + weighted fair queuing at the dispatcher. For storage, keep tenant sharding and **split only hot tenants** into `tenant#bucket` via a directory. → [[wiki/system-design-concepts/fair-share-scheduling]], [[wiki/system-design-concepts/hot-key-write-contention]].

## Follow-up: capacity, store choice, claim shape

- **Compare lease granularities at the same interval.** Per-job 6M/30 s = 200k/s vs per-driver 6,000/30 s = 200/s. Batching per-job lease rows cuts tx, not row writes.
- **40k rows/s ≠ 40k jobs.** Four *different* cohorts move each second (inserted, claimed, started, finished). Heartbeats don't touch execution rows.
- **Rows/s and tx/s are separate numbers.** Driver-direct status batching yields ~3 rows/tx (≈6k tx/s) because a driver sees only ~3.4 events/s; a per-shard status writer fixes it (~50 tx/s). Fleet total ≈ 40k rows/s in ~370 tx/s.
- **Driver lease blast radius.** "Driver lease ⇒ 1,000 retries" is real, but per-job leases don't fix it — the driver renews them. Fix: standby driver + re-adoption within the worker keepalive grace.
- **Nested leases:** inner lease + reaction time must fit inside the outer; worked timeline (60 s / 20 s / 5 s) in [[wiki/system-design-concepts/lease-and-fencing]].
- **DynamoDB is about availability, not load.** At 200–600 writes/s Postgres holds leases fine; the alternative to DynamoDB is lease > worst failover (Aurora ~10–30 s vs Multi-AZ 60–120 s) plus an SLA carve-out.
- **Redis isn't forced by contention.** `SKIP LOCKED` + disjoint rows (scheduler inserts minutes out, dispatchers lock seconds out). The real Postgres risk is dead index entries under a long transaction. The "dispatch survives RDS down" argument only holds for the lookahead window and only with DynamoDB claims.
- **Two-step claim.** Holding 200 rows locked while the app picks pools ties transaction length to app code (pins vacuum, holds connections). Split: claim-to-dispatcher → in-memory placement → fenced per-row assign; dispatcher lease + reaper with `fence+1`. Redis wouldn't remove the second step — a Lua pop is a non-durable claim.
- **Landing design:** sharded Aurora only; add DynamoDB/Redis only if the failover carve-out is unacceptable.

## Connections
- The crux: [[wiki/system-design-concepts/lease-and-fencing]] — claim-by-CAS, fenced transitions, self-termination on lease loss.
- The design as a whole: [[wiki/system-design-concepts/distributed-job-scheduler]].
- Correctness contract: [[wiki/system-design-concepts/exactly-once-semantics]] — at-least-once + idempotency key; why exactly-once doesn't extend to arbitrary side effects.
- Capacity: [[wiki/theory/littles-law]], [[wiki/theory/latency-numbers]].
- DB ↔ Redis sync: [[wiki/system-design-concepts/transactional-outbox]].
- Hot tenant: [[wiki/system-design-concepts/fair-share-scheduling]], [[wiki/system-design-concepts/hot-key-write-contention]].
- Same claim seam: [[sources/docs/design-uber-driver-allocation-mock-interview]] / [[wiki/system-design-concepts/dispatch-and-matching]] (atomic ride claim).
- Follow-up pages: [[wiki/system-design-concepts/postgres-as-a-work-queue]], [[wiki/system-design-concepts/database-write-capacity]], [[wiki/tech/aws-rds-postgresql]] (failover windows).
- Driver as trust boundary: [[wiki/system-design-concepts/agent-tool-sandboxing]]; background on scheduling: [[sources/docs/os-scheduling-to-distributed-scheduling]], [[wiki/system-design-concepts/preemption-economics]].

## Concepts flagged for wiki (created with this extract)
- **Lease + fencing token** — claim via CAS, fenced transitions, self-termination on lease loss. *(new: [[wiki/system-design-concepts/lease-and-fencing]])*
- **Little's law** — concurrency = rate × duration. *(new: [[wiki/theory/littles-law]])*
- **Transactional outbox + sweeper** — DB as truth, cache as latency hint. *(new: [[wiki/system-design-concepts/transactional-outbox]])*
- **Fair-share scheduling** — per-tenant weighted fair queuing over worker capacity. *(new: [[wiki/system-design-concepts/fair-share-scheduling]])*
- **Distributed job scheduler** — the reference design. *(new: [[wiki/system-design-concepts/distributed-job-scheduler]]; revised with the follow-up)*
- **Postgres as a work queue** — `SKIP LOCKED`, two-step claim, dead tuples. *(new: [[wiki/system-design-concepts/postgres-as-a-work-queue]])*
- **Database write capacity** — rows vs tx vs IOPS vs memory vs disk; WCU. *(new: [[wiki/system-design-concepts/database-write-capacity]])*
