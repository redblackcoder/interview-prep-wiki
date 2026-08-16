# AWS RDS for PostgreSQL

Managed PostgreSQL: AWS owns patching, backups, host replacement, and failover; you own schema, queries, indexing, and the durability/replication posture. The staff+ material is in the **deployment topologies** (which determine node count, readability, and RPO), the **storage engine choices** (which determine IO cost), and the **durability knobs** you can actually tune.

## Deployment topologies — node count, readability, RPO

| Topology | Postgres nodes | Replication | RPO | Standby readable? |
|---|---|---|---|---|
| **Single-AZ** | 1 | — | data loss on failure | n/a |
| **Multi-AZ instance** | **2** (1 primary + 1 standby) | Synchronous, AWS-managed | ~0 | **No** |
| **Multi-AZ DB cluster** | **3** (1 writer + 2 readers, one per AZ) | **Semi-synchronous** — commit acked when ≥1 of 2 standbys confirms | ~0 | **Yes** |
| **Read replica** | +1 each | **Asynchronous** | > 0 (lag) | Yes (promotable) |

**The readability difference is a product choice, not a technical limit.** Both Multi-AZ topologies use the same PostgreSQL physical streaming replication, and any physical standby is *capable* of serving reads (`hot_standby`). AWS keeps the **instance** standby closed — its only job is to be a pristine, instantly-promotable failover target, so its WAL replay never competes with client queries. The **cluster** opens both standbys read-only behind a reader endpoint (and accepts that tradeoff *because it has two* standbys to spread read load and failover risk). Want reads off the instance model? You add a read replica — a separate, async feature.

- **Multi-AZ instance = 2 billable nodes, only 1 usable** (standby is idle failover capacity → compute cost ≈ 2×).
- **Multi-AZ DB cluster = 3 billable nodes, all 3 usable** (1 write + 2 read) — better $/node, faster failover, readers serve traffic.
- **RDS Proxy** — managed connection pooler; priced per vCPU-hour of the instance. Essential for serverless/high-connection apps (each Postgres connection ≈ a backend process using memory; `max_connections` is finite).

Why quorum-of-1 for the cluster? Requiring *both* standbys to confirm every commit (fully synchronous) means one slow/down reader stalls all writes. `ANY 1 (r1, r2)` keeps RPO≈0 (every acked write is on ≥1 other AZ) while surviving one standby being slow. The rare edge: writer + the one confirming reader both die simultaneously while the other lagged → RPO≈0 in practice but not *provably* zero under simultaneous double failure.

## Storage engines — this drives IO cost

| Type | Storage $/GB-mo | IO characteristics | When |
|---|---|---|---|
| **gp3** (default) | ~$0.115 | Baseline 3,000 IOPS + 125 MBps free (<400 GiB). ≥400 GiB: baseline 12,000 IOPS/500 MBps; provision more at $0.02/IOPS-mo & $0.04/MBps-mo | Almost everything; decouples IOPS from size |
| **gp2** (legacy) | ~$0.115 | 3 IOPS/GB, burst to 3,000 — IOPS *tied to size* | Legacy; prefer gp3 |
| **io2 Block Express** | ~$0.125 | Provision IOPS explicitly (~$0.10/IOPS-mo first tier), up to 256K, sub-ms, 99.999% durable | Latency-critical, high sustained IOPS |

**The gp3 small-volume IOPS trap:** a 100 GB gp3 volume is capped at 3,000 IOPS — you cannot provision more until it's ≥400 GiB. So a small-but-hot DB often gets over-provisioned to 400–500 GB purely to unlock the 12,000-IOPS baseline, or moved to io2. A real design+cost decision, not a footnote.

## Durability knobs you can actually tune

"Synchronous" is a *choice*, not a fixed trait of RDS. Two independent levers:

1. **Topology** (above) — the single-standby Multi-AZ standby is always synchronous (that's its contract; an async standby is by definition a read replica).
2. **`synchronous_commit`** — a modifiable DB parameter, the standard Postgres knob trading durability for write latency:
   - `on` (default) — wait for local WAL flush (+ sync standby)
   - `remote_write` / `remote_apply` — wait for standby to receive / apply
   - `local` — flush locally, don't wait for standby
   - `off` — don't even wait for local flush; commit returns early, risking a few hundred ms of recent commits on crash

Setting it to `off`/`local` weakens effective RPO **even under Multi-AZ**, because it changes *when* Postgres flushes WAL, upstream of replication. Crucially, even `off` never risks *corruption* — MVCC/WAL ordering is preserved; it only widens the recent-commit loss window. Teams do this deliberately for high-throughput, loss-tolerant write paths (event ingestion) while keeping Multi-AZ for HA. What RDS does *not* expose is `synchronous_standby_names` — the Multi-AZ standby's sync behavior is AWS-managed.

## Aurora PostgreSQL — the middle ground to name

Keeps the Postgres wire protocol but replaces storage with a distributed, 6-way-replicated log-structured layer (auto-grows, 15 low-lag replicas, faster failover). Its cost model introduces a **per-request IO charge**: Aurora **Standard** = lower instance/storage + ~$0.20 per 1M IO (cheap until IO-heavy, then unpredictable); Aurora **I/O-Optimized** = no per-IO charge, higher instance/storage (predictable for IO-heavy).

## Key points
- Multi-AZ *instance* = 2 nodes, standby closed, ×2 compute; Multi-AZ *cluster* = 3 nodes, readers open, semi-sync quorum-of-1.
- Readable-standby is a product-tier decision — same physical replication underneath.
- gp3 decouples IOPS from size *above 400 GiB*; below that you're capped at 3,000 IOPS — a classic sizing trap.
- `synchronous_commit` lets you relax durability for latency without touching topology; `off` still can't corrupt, only lose recent commits.
- Single writer is the relational scaling ceiling; Aurora *raises* it (better storage, faster failover, 15 low-lag read replicas) but is **still single-writer** — real write scale-out means sharding (Citus / app-level / Aurora Limitless).

## Interview angle

> "RDS Multi-AZ comes in two shapes. The instance form is two nodes — a primary and a synchronous standby that AWS keeps *closed*, purely a zero-RPO failover target — so you pay for two but use one. The cluster form is three nodes, a writer plus two readable standbys, and it acks a commit on a quorum of one of the two, so it's zero-RPO but survives a slow replica and the readers serve traffic. Readability there is a product choice, not a limit — it's the same streaming replication. And 'synchronous' isn't fixed: `synchronous_commit` lets me trade durability for write latency, down to `off`, which widens the loss window but never corrupts because WAL ordering holds. Async read replicas are the separate, RPO>0 feature."

## Connections
- [[system-design-concepts/rds-vs-key-value-store]] — RDS as the durable system-of-record side of the decision
- [[system-design-concepts/cloud-database-cost-model]] — where the instance/storage/IO axes come from; the gp3 small-volume trap
- [[tech/aws-elasticache-redis]] — the KV counterpart; contrast synchronous WAL commit vs async replicate + snapshot
- [[theory/durability-rpo-rto]] — WAL/fsync and RPO≈0 made concrete; `synchronous_commit` is the RPO-vs-latency knob
- [[theory/copy-on-write-vs-mvcc]] — MVCC is how Postgres serves consistent snapshots without readers blocking writers

## Sources
- [[sources/docs/rds-vs-kv-store-study-guide]] — §6 RDS specifics (topologies, storage, Aurora), §2 durability
- [rds-vs-kv-store-study-guide.html](https://github.com/redblackcoder/interview-prep-wiki/blob/master/sources/docs/rds-vs-kv-store-study-guide.html) — full comparison with pricing tables
