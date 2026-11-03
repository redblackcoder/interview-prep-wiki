# Latency Numbers Every Programmer Should Know

A back-of-envelope estimate is only as good as your sense of *which tier* an operation lands in. These are the order-of-magnitude latencies — Jeff Dean's canonical list, kept honest by Colin Scott's interactive model that drives every value off an exponential year-trend. Memorize the ratios, not the digits: the digits drift, the **tier structure and the physics floors don't.**

## The ladder

Grouped into the four tiers that actually matter, with the "if 1 CPU cycle were 1 second" human-scale column (×10⁹) that makes the gaps visceral:

| Operation | Latency | Tier | Human scale (×10⁹) |
|---|---|---|---|
| L1 cache reference | ~0.5–1 ns | **on-core** | ~1 s |
| Branch mispredict | ~5 ns | on-core | ~5 s |
| L2 cache reference | ~7 ns | on-core | ~7 s |
| Mutex lock/unlock | ~25 ns | on-core | ~25 s |
| **Main memory reference** | **~100 ns** | **on-node (RAM)** | ~1.5 min |
| Compress 1 KB w/ Snappy/Zippy | ~2–3 µs | on-node | ~1 hr |
| Read 1 MB sequentially from memory | ~100–250 µs | on-node | ~1.5 days |
| SSD random read | ~16 µs | on-node (flash) | ~4.5 hr |
| Read 1 MB sequentially from SSD | ~1 ms | on-node | ~11.6 days |
| **Round trip within same datacenter** | **~500 µs** | **cross-node (LAN)** | ~5.8 days |
| Disk seek | ~4–10 ms | spinning disk | ~4 months |
| Read 1 MB sequentially from disk | ~10–20 ms | spinning disk | ~7–8 months |
| **Packet round trip CA ⇄ Netherlands** | **~150 ms** | **cross-continent (WAN)** | ~5 years |

**Five anchors worth keeping in your head** — interpolate everything else from these:
`L1 ≈ 1 ns · RAM ≈ 100 ns · datacenter RTT ≈ 500 µs · disk seek ≈ 10 ms · cross-Atlantic RTT ≈ 150 ms`.

The tier boundaries are the point: **on-core (ns) → RAM (100 ns) → LAN/SSD (µs) → disk/WAN (ms)**. Each boundary is roughly ×1,000. A network hop costs ~5,000 memory references; crossing a continent costs another ~300× on top of that.

## Two axes that move on different curves

The single most useful mental model here: **latency and bandwidth are not the same number and do not improve together.**

- **Bandwidth keeps doubling** — you add lanes (more DRAM channels, more NIC/SSD parallelism). "Read 1 MB sequentially" keeps getting cheaper every year.
- **Latency stalls** — a single round trip can't be parallelized away. "One random memory reference" has been ~100 ns for two decades.

This is why **sequential ≫ random** at every tier: SSD random read ~16 µs vs 1 MB sequential from SSD ~1 ms *for 64× the data* — sequential amortizes the fixed access cost. On disk the seek (~10 ms) dominates so completely that sequential throughput is the only way to hide it.

## Physics-bound vs engineering-bound

The deepest classification — will a number ever improve?

| Class | Examples | Why |
|---|---|---|
| **Physics-bound (won't improve)** | Cross-continent WAN RTT (~150 ms); main-memory latency (~100 ns) | Speed of light through fiber over the distance; DRAM row-access + signal propagation have hard floors |
| **Engineering-bound (still improving)** | SSD/disk bandwidth, disk seek, NIC bandwidth | More parallelism, denser media, better mechanics — keeps doubling/halving |
| **Wall-hit (stopped ~2005)** | L1/branch/mutex (CPU-cycle-denominated) | Clock speed plateaued at ~3 GHz; we got more cores, not faster ones |

The takeaway for multi-region design: you can engineer around bandwidth and even disk latency, but you **cannot** engineer around cross-region RTT — it's the one cost that's a law of nature. Batch across it or move computation to the data.

## The scaling model (Scott's addition)

Each row is a function of `year`; the model's rates:

| Quantity | Trend |
|---|---|
| CPU cycle time | halved ~every 2 yrs **until ~2005**, then flat (clock wall) |
| Memory **latency** | ~7%/yr pre-2000, then **flat at ~100 ns** |
| DRAM / SSD **bandwidth** | doubles ~every 3 yrs |
| NIC bandwidth | doubles ~every 2 yrs |
| Disk **bandwidth** | doubles ~every 5 yrs (post-2002) |
| Disk **seek** | halves ~every 10 yrs |
| WAN RTT | **constant** (speed of light) |

Corollary: distrust any single memorized value. NVMe (~sub-10 µs) and RDMA-within-datacenter (single-digit µs) are already blurring the SSD/RAM and LAN tiers that this classic list separates.

## Key points
- Estimate by **tier**, not exact number: on-core ns → RAM 100 ns → LAN/SSD µs → disk/WAN ms, each boundary ~×1,000.
- **Latency stalls, bandwidth doubles** — so sequential access beats random at every tier, and "read 1 MB" gets cheaper while "one random reference" does not.
- **WAN RTT (~150 ms) and RAM latency (~100 ns) are physics-bound** and will never improve; most other numbers are still moving.
- Blocking a thread on cross-node I/O means stalling an on-core resource for ~1,000–1,000,000× its natural timescale — the whole justification for async I/O and green threads.
- The digits drift year to year; the **structure** (tiers, ratios, physics floors) is what's worth memorizing.

## Interview angle

> "I don't memorize the exact numbers, I memorize five anchors and the tier structure: L1 is ~1 ns, a RAM reference ~100 ns, a datacenter round trip ~500 µs, a disk seek ~10 ms, and a cross-Atlantic packet ~150 ms. Each tier boundary is about a thousand-fold. Two things drive design decisions: first, latency stalls while bandwidth keeps doubling, so sequential always beats random and batching always helps; second, some numbers are physics-bound — cross-region RTT is the speed of light and RAM latency has a hard floor — so you engineer around them with locality and batching rather than hoping hardware saves you. That's why a network hop costing ~5,000 memory references makes a chatty design fatal, and why blocking a thread on I/O is catastrophic."

## Connections
- [[system-design-concepts/rds-vs-key-value-store]] — the RAM-vs-SSD latency gap (100 ns vs µs–ms) is exactly why an in-memory KV store beats an on-disk RDBMS for the hot set
- [[system-design-concepts/cloud-database-cost-model]] — the *cost* counterpart: DRAM costs ~100–250× SSD per GB, the price you pay for the latency-tier jump
- [[theory/concurrency-primitives]] — the ns→ms gap is why blocking a thread on I/O is so expensive, and why green threads exist for I/O-bound concurrency
- [[theory/durability-math]] — write-path latency floors come from this ladder: fsync (disk) + replication ack (datacenter RTT) set the minimum durable-write latency
- [[theory/consistent-hashing]] / [[system-design-concepts/hash-vs-range-partitioning]] — cross-node RTT (~500 µs) is the cost that partitioning and data locality exist to avoid

## Sources
- [[sources/articles/latency-numbers-every-programmer-should-know]] — the ladder, the scaling model, and the latency-vs-bandwidth / physics-vs-engineering framing
- [Latency Numbers Every Programmer Should Know (interactive)](https://colin-scott.github.io/personal_website/research/interactive_latency.html) — Colin Scott's year-slider rebuild of Jeff Dean's list
