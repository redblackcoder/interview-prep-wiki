# Fair-Share Scheduling (Multi-Tenant)

In a shared execution platform the scarce resource is **worker capacity**, not scheduler CPU. A hot tenant doesn't break you by overloading a shard's processes — those scale horizontally — it breaks you by **filling every pool and starving everyone else**. Fair-share scheduling enforces each tenant's share of capacity at the point work is admitted to run.

## The one-sentence mental model

> **Scale the control plane horizontally; ration the data plane per tenant. The dispatcher picks the next job from the tenant furthest below its fair share, not from the head of one global queue.**

## Mechanisms
- **Per-tenant quotas** — hard caps on concurrent executions / starts per second; excess stays queued (or 429 on ad-hoc paths).
- **Weighted fair queuing / DRF** — one queue per tenant; dispatcher serves by virtual time weighted by the tenant's share (DRF when resources are multi-dimensional: CPU + memory). Same idea as CFS/EEVDF at the OS level.
- **Priority classes within a tenant** — high-priority is quota-bound (e.g. ≤10% of a tenant's jobs) so priority can't become a bypass.
- **Borrowing** — idle share is lendable, reclaimable by preemption or by not re-leasing ([preemption economics](preemption-economics.md)).

## Storage side: split only the hot few
Changing the shard key from tenant to job ID spreads load but costs: tenant-scoped reads become scatter-gather, tenant ops (quota, pause, delete) become cross-shard, and one tenant's spike smears across every shard (lost isolation). Better: **keep tenant sharding; split hot tenants into `tenant#bucket` sub-shards via a directory table**, and partition work *within* a shard by job ID across scheduler/dispatcher workers.

## Key points
- Fairness must be enforced where capacity is consumed (dispatch), not where work is created (scheduler).
- Isolation is a feature of tenant sharding — don't trade it away to fix a CPU problem.
- Quotas are also the admission-control lever for thundering herds.

## Interview angle

> "Shard CPU isn't the hot-tenant problem — scheduler and dispatcher scale out. Worker capacity is. So the dispatcher keeps per-tenant queues and serves by weighted fair share with hard quotas; a noisy tenant waits on its own queue. For storage I keep tenant sharding for isolation and only split the few hot tenants into buckets through a directory."

## Connections
- [hot key write contention](hot-key-write-contention.md) — hot tenant is a hot key at the tenant granularity
- [distributed job scheduler](distributed-job-scheduler.md) — where the dispatcher applies it
- [cfs completely fair scheduler](../theory/cfs-completely-fair-scheduler.md), [eevdf scheduler](../theory/eevdf-scheduler.md) — the OS-level versions of fair share
- [rate limiting](rate-limiting.md) — per-tenant admission on the ad-hoc path
- [serving constrained resources](serving-constrained-resources.md) — scarce-backend admission control

## Sources
- [distributed job scheduler](../problems/system-design/distributed-job-scheduler.md) — hot tenants: a shard-key change alone isn't enough; fair share is the missing piece
