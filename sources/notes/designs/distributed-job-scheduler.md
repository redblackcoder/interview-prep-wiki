# Distributed job scheduler

Anonymized candidate design notes.

**Sharding.** Data store sharded by **tenant ID**; each service deployment works on a subset of shards/tenants.

**Data model.**
1. **Job definition** — what the user creates: schedule, parameters, pointer to binary (binary lives in object store; table is metadata).
2. **Job execution** — one instance of a definition; one-to-many from definition. Has **status** (`queued → running → success | failed`) and an **attempt count** with a retry limit.

**Scheduler.** Polls the DB every couple of minutes for definitions due in the **next 5 minutes**; transactionally creates `queued` execution rows. Poll interval deliberately **shorter than the lookahead window** so a failed loop gets a retry before the jobs are due.

**Dispatcher.** Looks at executions due in the next ~5–10 s (configurable) and issues them to workers **ahead of time with a delay** — e.g. assign 5 s before midnight, worker waits 5 s, starts exactly at midnight. Leaves time for retries while hitting the start SLA. Polls every few seconds.

**Redis as dispatch cache.** Polling the relational DB every few seconds would be heavy I/O, so executions due in the next ~minute are also written to a **Redis cluster**. No distributed transaction — use a **transactional outbox**: the execution insert also writes an outbox row "needs to go to Redis"; mark it done once written; a **reconciliation job** re-pushes anything unsynced. Redis entries have a **TTL (~30 min)**; the DB remains the system of record.

**Placement / priority.** Dispatcher picks a target by heuristic (capacity), and can implement **priority** — separate Redis sets per priority class, high priority dispatched first.

**Exactly-once (as presented).** Dispatcher submits to worker → updates execution to `running` in DB → deletes from Redis. If the Redis delete fails, the next poll finds it, checks the DB status, sees `running`, skips. If the dispatcher submits but crashes before the DB write, the worker double-checks/flips status to `running` before starting.

**Driver (sandbox trust boundary).** Workers run arbitrary user binaries, so they must not have DB / object-store credentials. Introduce a **driver**: dispatcher → driver → worker. The driver is trusted control code — reports status, writes to DB — and hands the job to a sandboxed worker.

**Two-level resource management.** Dispatcher does coarse placement at the **pool** level (each driver owns a pool of workers); the driver does fine-grained worker selection. Reduces dispatcher metadata and speeds decisions.

**Heartbeats.** Driver heartbeats job progress to the DB; missing heartbeats ⇒ job considered dead and eligible for retry.


The submit/status sequence above left the atomic claim unresolved.
