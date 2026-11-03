# Distributed Job Scheduler

Reference design for a multi-tenant cron + ad-hoc job platform (Airflow/Temporal/k8s CronJob/AWS EventBridge-scale): definitions become executions, executions are claimed and dispatched to sandboxed workers, and every run leaves a history. The crux is **not** the pipeline shape — it's the per-execution **claim/lease state machine** and the **per-running-job load** that falls out of Little's law.

## The one-sentence mental model

> **Materialize ahead, claim by CAS, run under a lease, and treat every cache as a hint: the DB row is the only truth, every transition is a fenced conditional write, and execution is at-least-once with a stable idempotency key.**

## Components
| Component | Job | Notes |
| --- | --- | --- |
| **Definitions** (tenant-sharded DB) | cron, params, binary pointer (object store), `version`, `enabled` | |
| **Scheduler** | definition → `QUEUED` execution rows for the next ~5 min | poll interval < lookahead window so a failed loop can retry; executions record `definition_version`; inserts batched (~500 rows/tx) |
| **Dispatcher** | two-step claim from the DB, picks a pool per job, hands off | fair share per tenant ([[system-design-concepts/fair-share-scheduling]]); holds a **dispatcher lease**; pre-dispatch a few seconds early with a start delay to meet the start SLA |
| **Driver** (trusted, one per pool + a standby) | fine-grained worker placement within a pool; holds the **driver lease**; reports status | control plane; holds DB credentials |
| **Worker supervisor** | runs the user's binary; kills it on missed driver keepalive | inner lease of the nested pair ([[system-design-concepts/lease-and-fencing]]) |
| **Status writer** (per shard) | batches driver status events into fenced multi-row updates | fixes the ~3 rows/tx problem (see Capacity) |
| **Telemetry store** | heartbeats-as-metrics, resource use, logs | KV / time-series; state machine stays in the DB |

Redis (due-index cache) and DynamoDB (lease/fence store) are **optional add-ons**, not the baseline — see "Store choice".

## State machine
`QUEUED → CLAIMED(dispatcher) → ASSIGNED(pool) → RUNNING → SUCCEEDED | FAILED | CANCELLED`; `attempt` counter with a retry limit. Every arrow is a conditional write:
- **claim (tx 1):** `WHERE state='QUEUED' AND definition_version=:v AND enabled`, `SKIP LOCKED`, sets `fence=fence+1`, `dispatcher_id`, `dispatcher_epoch`
- **assign (tx 2):** `WHERE fence=:f AND state='CLAIMED'`, sets `owner_pool`, `driver_epoch` per row
- **later:** `WHERE fence=:f`
- **reapers:** a dead dispatcher's `CLAIMED` rows go back to `QUEUED` with `fence+1` after its lease + margin; a dead pool's rows after the driver lease + margin.

The two-step claim exists so **no transaction stays open across application logic** (pool placement). Detail and SQL: [[system-design-concepts/postgres-as-a-work-queue]].

## Leases: per driver, not per job
- One lease row per driver (`driver_leases`), renewed by the driver; execution rows are **not** touched by heartbeats.
- Same renewal interval on both sides: per-job = 6M / 30 s = **200k writes/s**; per-driver = 6,000 / 30 s = **200 writes/s**. The ratio is always jobs-per-driver.
- **A driver crash must not retry its 1,000 jobs.** Per-job leases don't save them either — the driver is what renews them. The fix is **re-adoption**: a standby driver bumps the epoch (`WHERE epoch=7 → 8`), worker supervisors reconnect within their keepalive grace, and one batched write re-points the jobs (`SET driver_epoch=8 WHERE owner_pool=:p AND driver_epoch=7`). Retries happen only when a whole pool is cut off.
- **Driver lease > worst DB failover** (e.g. 60 s vs Aurora ~30 s), so a failover doesn't trigger mass self-kill.

## Capacity (10k starts/s, 10-min average)
- Running = 10k × 600 = **6M** ([[theory/littles-law]]).
- **Row writes ≈ 40k/s:** in any one second four *different* cohorts move — 10k inserted (due in ~5 min), 10k claimed (due now), 10k go RUNNING, 10k finish (started ~10 min ago). Two-step claim adds 10k ASSIGNED; heartbeats add 200.
- **Transactions are the other number** — batching cuts commits, not row writes:

| Writer | Rows/s | Rows per tx | Tx/s |
| --- | --- | --- | --- |
| Scheduler inserts | 10k | 500 | 20 |
| Dispatcher claim + assign | 20k | 200 | 100 |
| Status, driver-direct | 20k | **~3** | **~6k** |
| Status via per-shard writer (100 ms flush) | 20k | ~400 | ~50 |
| Driver-lease heartbeats @ 30 s | 200 | 1 | 200 |

- Driver-direct batching barely helps: a 1,000-job driver with 600 s jobs sees only ~1.7 starts + 1.7 finishes per second, and tenant sharding spreads those over several shards. Route status through a per-shard writer (e.g. Kafka partitioned by shard) → **~370 tx/s total**.
- Hot working set: 9M queued + running rows × ~500 B ≈ 4.5 GB + indexes → fits in RAM. Disk: ~864M rows/day ≈ 0.9 TB/day → partition by day, keep ~7 days hot, archive to S3.
- Cron peaks (00:00, top of hour, 1st of month) are **known ahead** from the materialized window → pre-scale pools.
- How to reason about each number (rows, tx, IOPS, memory, disk) and the DynamoDB equivalent: [[system-design-concepts/database-write-capacity]].

## Store choice
**Baseline: sharded Aurora PostgreSQL only.** At ~40k rows/s and a few hundred tx/s, 4–6 shards with headroom.
- Dispatch contention doesn't force Redis: `SKIP LOCKED` means dispatchers don't block each other, and the scheduler inserts rows due in ~5 min while dispatchers lock rows due in the next 5 s — disjoint rows.
- The real Postgres risk is **dead index entries under a long transaction** (vacuum can't clean past it) — operational fix, not an architectural one.
- Aurora failover ≈ 10–30 s (shared storage, no data copy) vs RDS Multi-AZ 60–120 s. Accept a dispatch pause during failover and **say it in the SLA**: "p99 start within 1 s, except during a DB failover."

**Add-ons and what they actually buy:**
| Add | Buys | Doesn't buy |
| --- | --- | --- |
| **DynamoDB** for lease/fence | no failover window on the claim/lease path; per-job leases at 200k WCU if ever needed | load relief at 200–600 writes/s (not needed); cross-region CAS (global tables are last-writer-wins) |
| **Redis** due-index | lower pick latency; dispatch through an RDS failover **only when combined with DynamoDB claims** and only for the lookahead window | removing the durable claim — a Lua pop is a non-durable claim, so it's still two steps + two reapers |

If you go DynamoDB + Redis, DynamoDB becomes authoritative for `CLAIMED → … → terminal` (`attribute_not_exists` on claim, `fence = f` after), events go through Kafka, and RDS is a projection applied with `WHERE fence <= f`. That's three extra systems to survive a ~30 s event — justify it with an SLA, not by default.

## Guarantees to state up front
- **At-least-once execution**, at most one live lease-holder at a time; `execution_id + attempt` passed to the job as an idempotency key; optional at-most-once mode (never retry after an uncertain start).
- **Start SLA as p99 per priority class**, with priority quota-bound; spread/jitter flexible schedules (Jenkins `H`); carve out DB failover.
- Execute-now: DB insert (via outbox) + nudge; per-tenant rate limit, 429.
- Edits/deletes/pauses: enforced at claim via version/enabled predicate; deletes are tombstones.

## Key points
- Claim **before** submit; "check status then act" is a race.
- Never hold a transaction open across application logic — split the claim.
- Don't split the state machine across stores unless an SLA demands it; move only high-volume telemetry out.
- Size **rows/s and tx/s separately**; batching fixes tx/s only when batches are actually large.
- Keep tenant sharding (isolation, tenant-scoped reads); split only hot tenants.
- Two-level placement (pool → worker) keeps dispatcher metadata small — Mesos-style.

## Interview angle

> "Scheduler materializes five minutes ahead into sharded Aurora. Dispatchers claim in two steps — a `SKIP LOCKED` batch claim to themselves, then a fenced per-row assign to pools — so no transaction spans placement logic. Drivers hold one lease each, not one per job: 200 writes a second instead of 200k, and a standby driver re-adopts the pool so a crash isn't a thousand retries. Status goes through a per-shard writer so commits carry hundreds of rows. That's about 40k row writes and a few hundred transactions a second — five shards. The lease outlasts an Aurora failover, and I carve the failover out of the one-second SLA. I'd add DynamoDB and Redis only if that carve-out is unacceptable."

## Connections
- [[system-design-concepts/lease-and-fencing]] — the crux; nested leases and re-adoption
- [[system-design-concepts/postgres-as-a-work-queue]] — `SKIP LOCKED`, two-step claim, dead-tuple risk
- [[system-design-concepts/database-write-capacity]] — rows vs tx vs IOPS vs memory vs disk; Postgres vs DynamoDB
- [[system-design-concepts/transactional-outbox]] — DB → cache sync if Redis is added
- [[system-design-concepts/fair-share-scheduling]] — hot tenant / worker capacity
- [[system-design-concepts/exactly-once-semantics]] — why the contract is at-least-once + idempotency key
- [[theory/littles-law]] — capacity derivation
- [[tech/aws-rds-postgresql]] — Aurora vs Multi-AZ failover
- [[system-design-concepts/agent-tool-sandboxing]] — driver/worker trust boundary
- [[system-design-concepts/preemption-economics]], [[system-design-concepts/compressible-vs-incompressible-resources]] — cluster-scheduler economics underneath the worker pools
- [[system-design-concepts/work-distribution]] — partitioning scheduler/dispatcher work within a shard

## Sources
- [[sources/docs/design-job-scheduler-mock-interview]] — the mock design round: capacity, store choice, two-step claim
- [[sources/docs/os-scheduling-to-distributed-scheduling]] — OS → cluster scheduling background
