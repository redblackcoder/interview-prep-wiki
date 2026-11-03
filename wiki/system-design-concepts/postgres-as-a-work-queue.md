# Postgres as a Work Queue

Using a relational table as the durable queue that many workers pull from concurrently (Oban, pg-boss, Que, Solid Queue; the dispatch table of a job scheduler). It holds up well into thousands of claims per second per shard — **if** you claim with `SKIP LOCKED`, never hold a transaction across application logic, and protect vacuum from long transactions.

## The one-sentence mental model

> **Lock rows for one statement, not for one thought: `FOR UPDATE SKIP LOCKED` lets workers take disjoint batches without waiting on each other; anything that needs app logic between "pick" and "commit to a destination" becomes a second, fenced transaction.**

## How it works

### Batch claim with `SKIP LOCKED`
```sql
WITH picked AS (
  SELECT id FROM executions
  WHERE state = 'QUEUED' AND scheduled_at <= now() + interval '5 seconds'
  ORDER BY scheduled_at LIMIT 200
  FOR UPDATE SKIP LOCKED)
UPDATE executions e
SET state = 'CLAIMED', fence = e.fence + 1,
    dispatcher_id = :d, dispatcher_epoch = :dep
FROM picked WHERE e.id = picked.id
RETURNING e.id, e.fence, e.tenant_id, e.resource_class;
```
One autocommit statement: locks live for milliseconds. Worker B skips rows worker A has locked and takes the next 200. Backed by a partial index on `(scheduled_at) WHERE state = 'QUEUED'`.

### Why not one transaction with per-row destinations
If each row needs its own destination (a pool, a shard, a worker), the tempting version is `BEGIN; SELECT … FOR UPDATE SKIP LOCKED; <app decides>; UPDATE … FROM (VALUES …); COMMIT`. Row contention is fine (`SKIP LOCKED`), but the **transaction length is now set by app code**. A GC pause or a hung process:
- holds a connection,
- pins vacuum's xmin horizon (dead rows pile up — see below),
- hides the whole batch from every other worker.
`idle_in_transaction_session_timeout` caps it; it doesn't fix it.

### Two-step claim (the fix)
1. **Claim to self** — the statement above.
2. **Decide in memory**, no transaction open.
3. **Assign, fenced per row:**
```sql
UPDATE executions e
SET state = 'ASSIGNED', owner_pool = v.pool, driver_epoch = v.epoch
FROM (VALUES (101, 4, 'pool-7', 8),
             (102, 2, 'pool-3', 5)) AS v(id, fence, pool, epoch)
WHERE e.id = v.id AND e.fence = v.fence AND e.state = 'CLAIMED';
```
4. **Release what didn't fit:** `UPDATE … SET state='QUEUED' WHERE id = ANY(:unplaced) AND state='CLAIMED' AND dispatcher_id=:d`.
5. **Reaper for dead claimers:** claimers hold a lease; after it expires + margin, `UPDATE … SET state='QUEUED', fence=fence+1 WHERE state='CLAIMED' AND dispatcher_id=:dead AND dispatcher_epoch=:ep`. The `fence+1` makes a paused-not-dead claimer's step 3 fail, so one job can't reach two destinations.

Cost vs one transaction: one more state, one more reaper, ~2× claim tx/s (50 → 100 at 10k claims/s). Cheap.

### Multi-row fenced updates for status
The same `UPDATE … FROM (VALUES (id, fence, new_state) …) WHERE e.fence = v.fence` batches status changes; rows affected < rows sent means those rows were fenced out (zombie writer). Batches only help if they're large — collect them in a per-shard writer, not per producer.

## The real risk: dead rows, not locks
Every claim moves a row out of the `QUEUED` partial index and leaves a dead entry until vacuum removes it. Normally that's quick. **One long transaction anywhere on the primary** (a 10-minute analytics query, a stuck claimer) stops vacuum from removing anything newer: at 2k claims/s per shard × 600 s = **1.2M dead entries** exactly where every claim query scans. Symptoms: claim latency climbs, CPU climbs, nothing is "locked".
- No long transactions on the primary — reads to replicas; `idle_in_transaction_session_timeout`, `statement_timeout`.
- Partition by day so old partitions are dropped, not vacuumed.
- Aggressive autovacuum on the hot partition; don't index frequently-updated columns (keeps updates HOT).

## Key points
- Contention between pullers is solved by `SKIP LOCKED`; inserts of future work never wait on row locks, so producers and consumers don't collide.
- Keep lock scope = one statement. App logic between pick and commit → split into claim + fenced assign.
- Size claims/s per shard and batch size → tx/s; a few thousand claims/s per shard is well within range.
- Watch vacuum health (dead tuples, xmin age) as the leading indicator, not lock waits.
- A cache in front (Redis ZSET) doesn't remove the durable claim — a Lua pop is a non-durable claim, so you still write the DB and now run two reapers.

## Interview angle

> "I'd dispatch straight from Postgres: a `SKIP LOCKED` batch claim so dispatchers never wait on each other, and the scheduler's inserts are for rows minutes out, so they don't collide either. I won't hold that transaction open while I pick pools — that ties transaction length to app code and pins vacuum. So it's two steps: claim to myself, decide in memory, then a fenced per-row assign; a reaper returns claims from a dead dispatcher with a fence bump. The thing I actually monitor is dead tuples under long transactions, not locks."

## Connections
- [[system-design-concepts/distributed-job-scheduler]] — the dispatch path this was worked out for
- [[system-design-concepts/lease-and-fencing]] — claim = CAS, fence on every later write, reaper after lease + margin
- [[system-design-concepts/database-write-capacity]] — rows vs tx; why batch size matters
- [[theory/copy-on-write-vs-mvcc]] — MVCC is why claims leave dead tuples and why long transactions hurt
- [[tech/aws-rds-postgresql]] — the managed Postgres underneath; failover windows
- [[system-design-concepts/transactional-outbox]] — the related "DB row is truth, everything else is a hint" pattern

## Sources
- [[sources/docs/design-job-scheduler-mock-interview]] — lock contention; one transaction vs a two-step claim
