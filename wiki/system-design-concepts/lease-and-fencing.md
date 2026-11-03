# Lease + Fencing Token

The mechanism for "exactly one worker owns this piece of work right now" in a system where workers crash, pause, and get partitioned. A **lease** is time-bounded ownership granted by a conditional write; a **fencing token** is a monotonically increasing number attached to that ownership so that a stale owner's writes are rejected. Together they turn "check the status, then act" (a race) into "win the CAS, then act" (a guarantee).

## The one-sentence mental model

> **Ownership is a conditional write, not a read. Whoever flips the row with `WHERE state='QUEUED'` owns it; every later write carries the fence token and fails if someone newer took over; and an owner that can't renew its lease stops itself before the lease expires.**

## How it works

### 1. Claim = compare-and-set
```sql
UPDATE executions
SET state='CLAIMED', owner=:me, fence=fence+1, lease_until=now()+30s
WHERE id=:x AND state='QUEUED'
-- rows affected = 1 → I own it; 0 → someone else does
```
Two dispatchers can read the same candidate; only one CAS succeeds. **Claim before side effects** (submit to worker), never after — "submit then mark" leaves a crash window that duplicates work.

### 2. Fenced transitions
Every subsequent write — `RUNNING`, heartbeat, `SUCCEEDED`/`FAILED` — is `WHERE id=:x AND fence=:f`. If the lease expired and a new owner claimed with `fence=f+1`, the old owner's writes affect 0 rows and it learns it's a zombie. Downstream systems that accept writes from owners (object store, external APIs) can check the token too — that's what makes it a *fence* rather than just a lock (Kleppmann's point against Redlock-style locks without tokens).

### 3. Lease renewal = heartbeat
The owner extends `lease_until` periodically (e.g. every lease/3). Missing renewals ⇒ the lease lapses ⇒ work is re-claimable. At scale, per-lease heartbeats are often the dominant write load — and **batching per-job lease rows cuts transactions, not row writes**. The real fix is to change the lease's granularity.

### Lease granularity: per job vs per host
Compare at the **same** renewal interval (6M jobs, 6,000 drivers, renew every 30 s):
| | Leases | Writes/s |
| --- | --- | --- |
| Per job | 6M | 200,000 |
| Per driver | 6,000 | 200 |

The ratio is always jobs-per-host. Execution rows then carry `driver_epoch` and are never touched by heartbeats.

**Objection: "a driver dies ⇒ 1,000 jobs retried."** Per-job leases don't prevent that either — the driver is the thing that renews them. The blast radius comes from the driver being the only control path. Fix it with **re-adoption**:
- a standby driver takes the driver-lease row (`epoch 7 → 8 WHERE epoch = 7`);
- worker supervisors reconnect within their keepalive grace (grace > takeover time), so no job is killed;
- one batched write re-points the jobs: `SET driver_epoch = 8 WHERE owner_pool = :p AND driver_epoch = 7`.
Retries then happen only when a whole pool is cut off — when nobody could supervise those jobs anyway. (Same as a kubelet restart not killing pods.)

### Nested leases: inner + reaction time must fit inside outer
Store → driver lease (outer), driver → worker keepalive (inner). Example: driver lease **60 s** (renew every 10 s), worker keepalive **20 s** (ping every 3 s), margin **5 s**.

**Driver loses the DB at t=0** (lease valid to t=60):
| t | Event |
| --- | --- |
| 0–35 | driver retries the DB |
| 35 | driver stops pinging workers (60 − 20 − 5) |
| 55 | workers hit 20 s without a ping → kill their jobs |
| 60 | lease expires in the DB |
| 65 | reaper requeues (60 + 5); new copies start |
Old copies die at 55, new start at 65 — **no overlap**. If the driver waited until t=50 to stop pinging, workers would run until 70 and overlap the new copies by 5 s.

**One worker loses its driver at t=0:** worker kills at t=20; driver marks `LOST` and requeues only after 20 + 5 = t=25. Requeue at t=10 would overlap for 10 s.

### Lease length vs store failover
If the lease store is unavailable longer than the lease, **every owner self-kills**. Either put leases in a store with no failover window (e.g. DynamoDB), or make the lease longer than the worst failover (60 s lease vs Aurora ~30 s). The price of a longer lease is slower dead-owner detection — acceptable once re-adoption makes whole-pool loss rare.

### 4. Self-termination (the half people forget)
A partitioned owner can't know it lost the lease — its job keeps running. So the owner must **fail closed**: if it can't renew by `lease_until − margin`, it kills its own work. The reclaimer waits `lease_until + clock-skew margin` before re-claiming. Overlap becomes bounded by skew instead of unbounded.

## Key points
- **The guarantee is "at most one *valid* owner at a time," not "work runs once."** For arbitrary side effects you still get at-least-once execution; pass `(id, attempt)` or the fence as an **idempotency key** so the work itself can dedup.
- **Leases need a time bound; locks without one deadlock on crash.** Locks without a fencing token are unsafe under GC pauses and partitions.
- **Clock skew matters on both sides** — use durations relative to the store's clock where possible (DB `now()`), and pad the reclaim.
- **Invalidate on mutation:** put extra predicates in the claim (`AND definition_version=:v AND enabled`) so paused/deleted work fails the claim instead of running.
- Where it shows up: job schedulers, work queues (SQS visibility timeout is a lease without a fence), leader election (etcd/ZooKeeper sessions + revision numbers), ride/driver assignment, shard ownership.

## Interview angle

> "Ownership is a conditional update: `UPDATE … SET state='CLAIMED', owner=me, fence=fence+1, lease_until=now()+30s WHERE id=:x AND state='QUEUED'` — one row affected means I won. I claim before I submit anything. Every later write carries the fence, so a zombie's writes bounce. Heartbeats renew one lease per driver, not per job — 200 writes a second instead of 200k — and a standby driver re-adopts the pool so a driver crash isn't a thousand retries. If a driver can't renew, it stops its worker keepalives early enough that workers are dead before the lease runs out, and nobody reclaims until expiry plus a skew margin. That gives me at-most-one live owner; execution is still at-least-once, so I hand the job a stable execution id as its idempotency key."

## Connections
- [[system-design-concepts/exactly-once-semantics]] — leases give at-most-one owner; the idempotency key closes the effect
- [[system-design-concepts/distributed-job-scheduler]] — the canonical application: dispatch claim, driver heartbeats, retry-after-expiry
- [[system-design-concepts/dispatch-and-matching]] — the same CAS claim on `{ride_id, driver_id}`
- [[system-design-concepts/distributed-id-generation]] — worker-ID leasing with self-fencing and `TTL + skew + margin` reclaim
- [[theory/consensus-raft]] — leader leases and terms are the same idea (term = fence)
- [[system-design-concepts/work-distribution]] — partition ownership in a fleet uses leases
- [[system-design-concepts/postgres-as-a-work-queue]] — two-step claim with a dispatcher lease and fenced assign
- [[system-design-concepts/distributed-file-system]] — expiring write handles and capacity reservations are leases; repair swaps a replica with a fenced `WHERE replica_set_version=:v`
- [[system-design-concepts/immutable-object-commit]] — idempotent commit keyed by `upload_id`; abandoned reservations expire

## Sources
- [[sources/docs/design-job-scheduler-mock-interview]] — the claim operation; lease granularity, nested-lease timeline, re-adoption
- [[sources/docs/design-distributed-file-system-interview]] — offset reservation that "dies" if not followed up = a lease; fenced replica swap in repair
