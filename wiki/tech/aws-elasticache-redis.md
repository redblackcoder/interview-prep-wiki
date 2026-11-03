# AWS ElastiCache (Redis / Valkey)

Managed in-memory data store, Redis-protocol compatible. The staff+ material: what **"persistence" actually guarantees** (weaker than a database), the **deployment models** and their node math, and the levers — **data tiering**, **Valkey pricing**, **single-thread-per-shard** — that shape both cost and failure modes.

## Engine: Redis OSS vs Valkey

After Redis relicensed, AWS made **Valkey** (the Linux-Foundation fork of Redis 7.2) the default, wire-compatible engine — and prices it **lower** than Redis OSS (~20–33% cheaper on serverless). Data model and ops are identical, so for new builds Valkey is usually the cost-rational choice.

## Persistence — best-effort, not transactional

The critical distinction for any "persisted store" requirement: **"Redis with persistence" ≠ "a durable database."** Open-source Redis offers two mechanisms; ElastiCache exposes a constrained subset:

- **RDB snapshots** — point-in-time fork-and-dump. In ElastiCache this is the *backup* mechanism (automatic daily + manual), stored in S3. RPO = time since last snapshot (up to 24h on defaults).
- **AOF (append-only file)** — logs each write, replayable on restart; *closer* to a WAL, but `fsync`'d on an interval (`everysec` → up to 1s loss).
- **Replication** — primary → replica is **asynchronous**. On primary failure, an acked write not yet shipped to the promoted replica is **lost**.

**The AOF + Multi-AZ gotcha (know this cold):** on ElastiCache, **AOF is not supported once Multi-AZ with automatic failover is enabled** (and is unavailable on many modern node types). So AWS's durability story is **replication + snapshots + fast automatic failover — not an fsync'd log.** Realistic RPO is "last snapshot + un-replicated writes since," and never provably zero. The correct staff+ pushback on "we'll persist the system of record in Redis": *Redis persistence protects against process/node restarts, but its durability is weaker than an ACID database — size the acceptable data-loss window explicitly* (see [[theory/durability-rpo-rto]]).

## Deployment models and node math

- **Node-based (provisioned)** — you pick node type & count. *Cluster Mode Disabled* = one shard (1 primary + up to 5 replicas). *Cluster Mode Enabled* = many shards, each with its own primary + replicas. **You pay per node** — a 3-shard × 3-node cluster is *9* billable nodes.
- **Serverless** — no nodes; AWS auto-scales. Pay for **data stored (GB-hour)** + **ECPUs** (compute/requests). Great for spiky/unknown load; pricier per-unit at steady high load (see [[system-design-concepts/cloud-database-cost-model]]).
- **Multi-AZ + auto-failover** — replicas in other AZs, promote on primary loss. This *is* the practical durability mechanism (it replaces AOF).
- **Data tiering (r6gd nodes)** — hot data in RAM, cold data spills to local NVMe SSD. The main lever to make "big Redis" affordable, at some latency cost for cold hits.

## Consistency is per-shard

Redis atomicity is **per hash slot / shard**. A `MULTI/EXEC` or Lua script touching keys on different shards is rejected unless you co-locate them with **hash tags** — `{user123}:profile` and `{user123}:cart` hash to the same slot. Cross-entity transactions that Postgres does trivially require deliberate key design here, or aren't possible. Cluster mode partitions across up to 500 shards (16,384 slots) — built for horizontal write scaling — but a single hot/big key can't be split, creating a hot shard.

## Single command thread per shard

Redis executes commands on **one thread per shard** (I/O threads in 6+/Valkey handle sockets, not command execution). Consequences:
- One slow `O(N)` command (`KEYS *`, a big `SMEMBERS`) blocks *every other client on that shard*.
- **RDB snapshotting `fork()`s the process** — copy-on-write can transiently double memory and stall the event loop on large datasets. This is why you leave **25–35% memory headroom**, which also means "persisted Redis" needs bigger nodes than raw dataset size implies — a hidden cost driver.

## Key points
- Default to **Valkey** for new builds — wire-compatible and cheaper.
- ElastiCache durability = async replication + S3 snapshots + failover; **AOF is off under Multi-AZ auto-failover** → RPO is never provably zero.
- Node-based bills per node × (primaries + replicas); serverless bills GB-hour + ECPU.
- Multi-key atomicity requires hash tags to co-locate keys on one slot.
- Leave ~30% memory headroom for the snapshot fork — a cost driver, not just a tuning note.
- Data tiering (r6gd) is the lever for large datasets that don't all need to be in RAM.

## Interview angle

> "ElastiCache is managed Redis — I'd pick Valkey now since it's wire-compatible and cheaper. The thing to be precise about is persistence: enabling it doesn't make Redis a database. Under Multi-AZ auto-failover AOF is off, so durability is async replication plus periodic S3 snapshots plus fast failover — the RPO is last-snapshot-plus-unreplicated-writes, never provably zero. Node-based you pay per node times replicas, and each shard is single-threaded, so one `KEYS *` stalls everyone and the snapshot fork needs ~30% headroom. Cluster mode scales writes horizontally, but multi-key transactions need hash tags to land on one slot. It's a fantastic cache and real-time layer; I wouldn't make it the sole store for money."

## Connections
- [[system-design-concepts/rds-vs-key-value-store]] — ElastiCache as the KV side of the decision
- [[system-design-concepts/cloud-database-cost-model]] — node-vs-serverless billing and the ~30% headroom cost driver
- [[tech/aws-rds-postgresql]] — the relational counterpart; synchronous WAL commit vs async replicate + snapshot
- [[theory/durability-rpo-rto]] — why snapshot-interval + replication-lag makes RPO non-zero
- [[system-design-concepts/rate-limiting]] — Redis's atomic `INCR` per shard is exactly what a global rate limiter's counter needs

## Sources
- [[sources/docs/rds-vs-kv-store-study-guide]] — §7 ElastiCache specifics, §2 durability, §4 performance/failure modes
- [rds-vs-kv-store-study-guide.html](https://github.com/redblackcoder/interview-prep-wiki/blob/master/sources/docs/rds-vs-kv-store-study-guide.html) — full comparison with pricing tables
