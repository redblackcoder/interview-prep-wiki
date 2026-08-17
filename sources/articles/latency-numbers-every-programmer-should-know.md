---
source: Latency Numbers Every Programmer Should Know (interactive)
source_url: https://colin-scott.github.io/personal_website/research/interactive_latency.html
author: Colin Scott (after Jeff Dean / Peter Norvig)
type: article
date_extracted: 2026-08-17
topic: theory / system-design-concepts
---

# Latency Numbers Every Programmer Should Know

> Colin Scott's interactive rebuild of Jeff Dean's classic latency table. The list
> was originally circulated by Jeff Dean (~2010) and popularized in Peter Norvig's
> "Teach Yourself Programming in Ten Years." Scott's contribution: each number is
> driven by an **exponential trend model** with a year slider, so you can watch which
> latencies improve over time and which are pinned by physics.

## The Ladder (canonical order-of-magnitude values)

These are the numbers worth memorizing — accurate to an order of magnitude, which is
all a back-of-envelope estimate needs. Grouped by tier:

| Operation | Latency | In human-scale terms (×10⁹) |
|---|---|---|
| L1 cache reference | ~0.5–1 ns | 1 s |
| Branch mispredict | ~5 ns | 5 s |
| L2 cache reference | ~7 ns | 7 s |
| Mutex lock/unlock | ~25 ns | 25 s |
| **Main memory reference** | **~100 ns** | ~1.5 min |
| Compress 1 KB w/ Zippy (Snappy) | ~2–3 µs | ~1 hr |
| Read 1 MB sequentially from memory | ~100–250 µs* | ~1.5 days |
| SSD random read | ~16 µs | ~4.5 hr |
| Round trip within same datacenter | ~500 µs | ~5.8 days |
| Read 1 MB sequentially from SSD | ~1 ms | ~11.6 days |
| Disk seek | ~4–10 ms | ~4 months |
| Read 1 MB sequentially from disk | ~10–20 ms | ~7–8 months |
| **Packet round trip CA → Netherlands → CA** | **~150 ms** | ~5 years |

*The 1 MB-from-memory number is the one people most often misremember; the classic
2012 value was 250 µs, and Scott's model has it dropping over time as DRAM bandwidth grows.

The five "anchor" numbers I actually keep in my head:
**L1 = ~1 ns, RAM = 100 ns, datacenter RTT = 500 µs, disk seek = 10 ms, cross-Atlantic RTT = 150 ms.**
Everything else can be interpolated from those.

## Scaling models (Scott's key addition)

Each row is a function of `year`. The model rates:

| Quantity | Trend |
|---|---|
| CPU cycle time | halved ~every 2 yrs **until ~2005**, then flat (~3 GHz clock wall) |
| Memory **latency** | improved ~7%/yr pre-2000, then **flat at ~100 ns** |
| Memory (DRAM) **bandwidth** | doubles ~every 3 yrs |
| NIC bandwidth | doubles ~every 2 yrs |
| SSD bandwidth | doubles ~every 3 yrs |
| Disk **bandwidth** | doubles ~every 5 yrs (post-2002) |
| Disk **seek** time | halves ~every 10 yrs |
| WAN RTT (cross-continent) | **constant** — bounded by speed of light |

## Key Ideas
- **Latency has tiers separated by ~orders of magnitude**, and the tier gaps are the
  point: cache (ns) → memory (100 ns) → LAN / SSD (µs) → disk / WAN (ms). Every design
  decision is really "which tier does this operation land in?"
- **Latency and bandwidth improve on different curves.** Bandwidth keeps doubling
  (add lanes); latency stalls (a single trip can't beat physics). This is why "read 1 MB
  sequentially" keeps getting cheaper but "one random memory reference" is stuck at 100 ns.
- **Some numbers are pinned by physics, not engineering.** Cross-continent RTT (~150 ms)
  is ~speed of light through fiber over that distance and will *never* improve. Memory
  latency plateaued because signal propagation + DRAM row access have hard floors.
- **The clock-speed wall (~2005)** is why the CPU-cycle-denominated numbers (L1, branch
  mispredict, mutex) stopped shrinking — we got more cores, not faster ones.
- **Sequential ≫ random.** SSD random read (~16 µs) vs 1 MB sequential from SSD (~1 ms
  for 64× the data): sequential amortizes the access cost. Same story for disk, where
  the seek (~10 ms) dominates and sequential throughput is the only way to hide it.

## My Understanding
- The real interview value of this table isn't the exact numbers — it's the **relative
  ratios** and knowing *which tier* an operation lives in. If I can say "a memory
  reference is ~100 ns, a datacenter round trip is ~500 µs, so a network hop costs ~5,000
  memory references," I can reason about whether a design's chattiness is fatal.
- The single most important divide is **on-node (ns–µs) vs cross-node (µs–ms)**. Crossing
  the network is ~1,000× a memory access; crossing a continent is another ~300×. This is
  the whole justification for locality, batching, and caching.
- Scott's year-slider drove home that **I should distrust any single memorized value** —
  SSDs and disk seeks got much faster since 2012, memory latency did not. The *structure*
  (tiers, ratios, physics floors) is stable; the digits drift.
- The "human-scale" column (multiply by 10⁹: 1 ns → 1 s) is the mnemonic I'll use to
  make the gaps visceral: an L1 hit is "1 second," a disk seek is "4 months," a
  cross-Atlantic packet is "5 years." That gap is why blocking on I/O is a catastrophe.

## Open Questions
- Scott's model predicts values forward; how far off are the ~2020 projections from
  measured 2020 hardware (esp. NVMe random read, which may already beat the ~16 µs SSD
  figure)? Worth spot-checking against real benchmarks.
- Where do modern additions land — CXL/persistent memory, RDMA within a datacenter
  (single-digit µs), NVMe (~sub-10 µs)? These blur the SSD/memory tier boundary.

## Connections
- Relates to: [[wiki/theory/durability-math]] — latency of fsync/replication acks (disk +
  datacenter RTT) sets the floor on write-path latency and durability trade-offs.
- Relates to: [[wiki/system-design-concepts/rds-vs-key-value-store]] — the memory-vs-disk
  and sequential-vs-random gaps are exactly why an in-memory KV store beats an on-disk RDBMS
  for hot reads.
- Relates to: [[wiki/theory/consistent-hashing]] / [[wiki/system-design-concepts/hash-vs-range-partitioning]] —
  cross-node RTT (~500 µs) is the cost that partitioning + locality try to avoid.
- Relates to: [[wiki/theory/concurrency-primitives]] — mutex (~25 ns) vs context switch
  vs network wait; the latency ladder is why blocking a thread on I/O is so expensive.
- Contrasts with: WAN RTT being physics-bound vs everything else being engineering-bound —
  a recurring theme in multi-region design.

## Key Quotes / Annotations
- The human-scale intuition (Brendan Gregg's "if a CPU cycle were 1 second"): L1 ≈ a few
  seconds, RAM ≈ minutes, disk ≈ months, cross-continent network ≈ years.
- Scott's framing: the table is "living" — every value is a function of time, and the
  interesting question is the *slope*, not the intercept.
