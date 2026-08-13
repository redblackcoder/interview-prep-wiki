# Cloud Database Cost Model: Instance, Storage, IO, Requests

Pricing a managed data store means decomposing the bill into four axes — **instance/compute, storage, IO, and requests** — plus backup and data transfer. The structural insight that wins interviews: **different stores concentrate cost on different axes**, and that concentration follows directly from where the data physically lives. Grounded in AWS RDS PostgreSQL vs ElastiCache (Redis/Valkey).

## The four axes, mapped

| Cost axis | RDS PostgreSQL | ElastiCache (node-based) | ElastiCache (serverless) |
|---|---|---|---|
| **Instance / compute** | Per-hour by class; ×2 for Multi-AZ instance, ×3 for MAZ cluster. RIs/Savings Plans −40→65% | Per-hour **per node** × (primaries + replicas). Reserved Nodes −30→60% | None explicit — folded into ECPU |
| **Storage** | Provisioned GB-month (gp3 ~$0.115). Pay for *allocated*, not used | **$0 separate** — storage *is* the node's RAM | **Data GB-hour** (Valkey ~$0.084, Redis OSS ~$0.125) |
| **IO** | gp3: extra IOPS $0.02/IOPS-mo + throughput $0.04/MBps-mo; io2 ~$0.10/IOPS-mo | **$0** — in-memory, no disk IO to bill | **$0** — no separate IO line |
| **Requests** | **$0** on standard RDS (Aurora Standard ≈ $0.20/1M IO) | **$0** — unlimited ops within node capacity | **ECPU**: ~$0.0023/M (Valkey), ~$0.0034/M (Redis OSS) |
| Backup | Free ≤ 100% of DB size; then ~$0.095/GB-mo | Free ≤ 1× cluster memory; then ~$0.085/GB-mo | Snapshot ~$0.085/GB-mo |
| Data transfer | Intra-AZ free · **cross-AZ ~$0.01/GB each way** · internet egress ~$0.09/GB (tiered) — applies to all three | | |

**The punchline:** RDS spreads cost across compute + cheap storage + IO. Node-based Redis concentrates nearly *all* cost into the node (RAM) line, with no storage/IO/request charges. Serverless Redis re-introduces storage-GB-hour + a per-request (ECPU) charge as the price of elasticity.

## Why the per-GB gap is ~100–250×

Redis stores every byte in DRAM. A `cache.r6g.2xlarge` gives ~52.8 GiB usable for ~$0.824/hr ≈ **$601/mo → ~$11.4 per GB-month of usable RAM**. You typically *double* that for a replica and can only fill a node ~70% (headroom for the snapshot `fork()`/copy-on-write and overhead), pushing effective cost toward **~$30+/GB-mo of durable capacity**. RDS gp3 SSD is **~$0.115/GB-mo**. That ~100–250× ratio is the whole reason Redis is a cache, not bulk storage — it "wins" only when µs-latency/high-throughput on the hot set is worth the premium.

## Node-based vs serverless decision rule

- **Steady, predictable, high-throughput → provisioned nodes.** You amortize a fixed node across huge request volume at $0/request.
- **Spiky, bursty, low-duty-cycle, or unknown → serverless.** Per-ECPU billing + scale-to-near-zero beats paying 24/7 for peak-sized nodes.
- Serverless storage at ~$0.084/GB-*hour* (~$61/GB-mo) is expensive for large always-on datasets — another reason serverless favors small/hot or bursty data.

## Worked contrast — 100 GB dataset, HA required

| | RDS PostgreSQL (Multi-AZ) | ElastiCache Redis (cluster, Multi-AZ) |
|---|---|---|
| Compute | db.r6g.2xlarge ×2 (standby) ≈ $1,413/mo | 3 shards × r6g.2xlarge + 1 replica each = 6 nodes ≈ $3,609/mo |
| Storage | 500 GB gp3 ≈ $57.50/mo | $0 (it's the RAM) |
| IO / requests | $0 (within baseline) | $0 |
| **On-demand total** | **~$1,470/mo** | **~$3,600/mo** |

Redis costs ~2.5× more here *and* gives a weaker durability guarantee and no ad-hoc queries — because you're renting 100 GB+ of DRAM to hold data Postgres keeps on cheap SSD. (The 500 GB sizing is deliberate: gp3 caps a small volume at 3,000 IOPS until it's ≥400 GiB — see [[tech/aws-rds-postgresql]].)

## Key points
- Always name **all four axes** — a bill is instance + storage + IO + requests, plus backup and cross-AZ transfer.
- Node-based Redis has **zero IO and zero request cost**; that's the flip side of paying full DRAM prices for storage.
- Multi-AZ multiplies the *compute* line (×2 instance, ×3 cluster); reserved commitments cut it 40–65%.
- Don't quote on-demand as the real bill — production uses Reserved Instances/Nodes; serverless is the one mode with no commitment discount.
- **Cross-AZ transfer (~$0.01/GB each way)** is a hidden line item at high replication/client throughput.

## Interview angle

> "I price a store on four axes — instance, storage, IO, requests — plus backup and cross-AZ transfer. The tell is that they concentrate differently: RDS spreads cost over compute, cheap SSD, and IO; node-based Redis puts almost everything in the node line because storage *is* RAM, so it has no IO or per-request charge; serverless Redis brings back a storage-GB-hour and a per-request ECPU charge to buy elasticity. The number that drives the design is the ~100–250× gap between DRAM and SSD per GB — that's why Redis is a hot-set cache and Postgres holds the bulk. Steady high load → provisioned nodes at $0/request; spiky → serverless."

## Connections
- [[system-design-concepts/rds-vs-key-value-store]] — the decision this cost model feeds; the RAM-vs-SSD economics is the mental model
- [[tech/aws-rds-postgresql]] — where the storage/IO axes live (gp3 vs io2, the small-volume IOPS trap)
- [[tech/aws-elasticache-redis]] — where the node-vs-serverless and headroom costs live
- [[system-design-concepts/preemption-economics]] — another "reason about the resource economics, not the feature" framing

## Sources
- [[sources/docs/rds-vs-kv-store-study-guide]] — §8 pricing model breakdown, §9 worked cost examples, §10 rate reference tables
- [rds-vs-kv-store-study-guide.html](https://github.com/redblackcoder/interview-prep-raw/blob/main/docs/rds-vs-kv-store-study-guide.html) — full itemized cost math and representative us-east-1 rates
