# Source Extract: Distributed Persistent KV Store — Mock Interview

Raw material from a principal-level system-design mock interview: *"Design a high-throughput, distributed, persistent key-value store for a cloud-scale service"* (Dynamo/DynamoDB-shaped). Captured for extraction into wiki pages. The interview stopped at the leaderless-vs-leader-based coordinator fork; the notes below cover the ground reached plus the concepts flagged for follow-up.

## §1 Problem framing

Two archetypes of "KV store": (a) **in-memory cache** (Redis-like) — optimizes throughput/tail-latency, durability best-effort because a system of record sits behind it; (b) **persistent distributed KV** (Dynamo-like) — it *is* the system of record, so durability/consistency dominate. Chosen target: the **persistent store**, because durability/replication is the hard part and a cache is that design with the durability dial turned down + eviction. Design the harder one, then relax.

Why KV over relational for this workload: the KV data model **refuses the features that force cross-node coordination** (joins, arbitrary multi-predicate queries, cross-partition transactions). Because it refuses them, every key maps deterministically to one partition and you scale by adding partitions. High write throughput is the *payoff* of that refusal, not merely "SQL writes are expensive."

## §2 Requirements & envelope (stated assumptions)

- **Functional:** string key → column-family of typed values (number/bool/string/blob). Point lookup by key; range queries over a **sort key**. `(partition_key, sort_key)` is the unique row identity.
- **Size caps:** key < 1 KB; value < 8 KB. **Sort key bounded small (~1 KB)** — anything indexed must be small (it lives in the index structure, is compared on every scan, replicated everywhere); the value is opaque so it can be large. (DynamoDB caps sort keys ~2 KB.)
- **Durability:** 6 nines. **Availability:** 4 nines (chosen deliberately — see downtime table).
- **Latency SLOs (derived from critical-path budgeting):** read < 10 ms p99 (one read ≈ 15 ms incl. ~2 ms network; a 20-deep read fan-out ≈ 300 ms p99, acceptable for interactive); write < 50 ms p99 (a 10-deep serial write path stays under ~1 s).
- **Throughput:** 1 region, ~1M reads/s peak, ~100K writes/s (read:write ≈ 10:1). Horizontally scalable; provisionable per-tenant below the ceiling.
- **Sizing tools named:** **Little's Law** (`concurrency = throughput × latency`): 100K/s × 10 ms = 1000 in-flight. **`node_count = max(throughput-bound, storage-bound)`** — the two can differ by 2 orders of magnitude; row size is a workload *fact*, not a knob to tune until the answer stops scaring you.

## §3 Availability nines → downtime

| Availability | Per year | Per month | Per day |
|---|---|---|---|
| 99% | 3.65 days | 7.2 h | 14.4 min |
| 99.9% | 8.76 h | 43.8 min | 1.44 min |
| 99.99% | 52.6 min | 4.38 min | 8.6 s |
| 99.999% | 5.26 min | 26.3 s | 0.86 s |

5 nines ≈ 5 min/yr ⇒ no human in the loop; every failure response must be automated. 4 nines ≈ one human-scale incident/month — the honest target for most infra. **Durability and availability are different axes:** "will I ever lose data" (copies + backups) vs "can I serve right now" (where copies sit + how you route around failure).

## §4 Replication factor, quorum, AZ failure

Failure model: **AZ + 1** (survive a full AZ loss plus one more node) across 3 AZs.

- **RF=3, one replica per AZ, W=2/R=2** survives a full AZ loss. The "+1 during an AZ outage" gap is covered by **hinted handoff / sloppy quorum**, *not* by raising RF.
- **RF=6 (2/AZ), W=4/R=3** was proposed to keep *reads* available through AZ+1 (loses 3, leaves 3; W=4 unmeetable ⇒ writes stop, R=3 meetable ⇒ reads continue). Internally consistent but costs **6× storage and 6× write amplification**. It is an *availability* decision, not a durability one.
- Strict-quorum AZ+1 across 3 AZs would need RF=9 (absurd) — the tell that replication is the wrong tool for that gap. If strong reads must survive AZ+1, the disciplined answer is **per-shard Raft, RF=5** (majority 3-of-5 survives an AZ), not sloppy RF=6.
- **Recommended default:** RF=3 + hinted handoff + async S3 backups.
- **Anti-pattern:** dropping quorum during an outage and "handling consistency in the app" — this violates W+R>N and exports version reconciliation to every tenant. The platform must absorb reconciliation (read-repair + anti-entropy, or consensus), never export it.

## §5 Durability math (AFR → nines)

Independent-failure model, per replica set, RF=3: `P(loss/set/yr) ≈ 6 · AFR³ · (MTTR/yr)²`. With AFR=2%, MTTR=4 h (=4.566e-4 yr): ≈ **1e-11/set/yr ≈ 11 nines/set**; across a 10⁴-set fleet ≈ **7 nines**. So **RF=3 + fast automated re-replication already clears 6 nines**. **MTTR (detect + re-replicate) is the real lever**, not RF: loss scales with `(MTTR/yr)^(RF−1)`, and MTTR is controllable (faster detection, parallel rebuild, spare capacity) whereas AFR is fixed hardware. Beyond RF=3, extra replicas barely move durability because the true ceiling is **correlated failure** (bad disk batch, power event, software bug corrupting all copies) — RF=6 replicates the bug six times. Backups are defense against correlated/logical loss + region DR + PITR, not operational durability (async ⇒ non-zero RPO; TB restore ⇒ RTO that blows the availability budget). Operational durability = **synchronous replication to W replicas**, RPO≈0.

## §6 Concepts flagged for follow-up (knowledge gaps)

- **Hinted handoff:** coordinator writes a down replica's copy to the next healthy ring node, tagged with a hint; handed back on recovery. Enables sloppy quorum ⇒ RF=3 stays writable through AZ+1 without raising RF. Requires read-repair + anti-entropy to fix the resulting drift.
- **Read repair:** on the read path, divergent replica versions ⇒ return newest, async write-back to stale replicas. Opportunistic convergence for hot keys.
- **Anti-entropy (Merkle-tree sync):** background reconciliation; replicas compare Merkle trees over key ranges root→down to find minimal divergent ranges without shipping all data.
- **Consensus (Raft/Paxos):** replica group agrees on an ordered write log; leader election, log replication, commit-on-majority.
- **Per-shard Raft, RF=5:** each shard is a Raft group; leader serves linearizable reads/writes; 3-of-5 majority survives an AZ.

## §7 Partitioning

- `hash(key) % N` is catastrophic: changing N remaps ~all keys ⇒ full reshuffle. Replace with **consistent hashing + virtual nodes** — the key→partition→node indirection so add/remove a node moves only ~1/N of data and spreads load evenly. Ring managed by a **control plane**, pushed to stateless compute nodes.
- **Hash vs range partitioning:** hash smears a contiguous range across all partitions ⇒ a global range scan is scatter-gather over everything ("a full table scan wearing a trenchcoat"). DynamoDB resolves it: partition by `hash(partition_key)`, **sort by sort_key *within* the partition**. Range queries are efficient only when scoped to a partition key; **global cross-partition range scans are given up** (escape hatch: a global secondary index = a second copy partitioned differently, at a cost).

## §8 Architecture reached

- **Compute/storage separation:** stateless compute fleet behind a load balancer (any request → any node), elastic, provisioned for sustainable not peak load; redundant storage layer beneath. Cheap node spin-up.
- **Coordinator pattern (re-derived):** compute node holds the ring, looks up the owning replicas, fans out, waits for quorum, acks. Range query: split into per-partition sub-ranges, scatter, gather, merge.
- **The unresolved fork (where the interview stopped):** *leaderless (Dynamo)* — compute node is coordinator, writes RF replicas directly, W acks, conflicts reconciled after the fact (version vectors + read-repair + anti-entropy); tunable, **not linearizable**. Vs *leader-based (Raft-per-shard)* — compute node sends to the shard **leader**, which replicates via consensus log; **linearizable**, at coordination + leader-election cost. Invoking both = paying for coordination twice. The W=2/R=2 quorum choice implies the leaderless lane. Historical anchor: **Amazon Dynamo (2007) chose leaderless; DynamoDB (2012) chose leader-per-partition + Paxos** — same company, opposite answer five years apart (operability of client-side conflict resolution was the pain).

## §9 Consistency spectrum (flagged for follow-up)

Weakest → strongest (single-object spine): eventual → session guarantees (read-your-writes, monotonic reads/writes) → causal → sequential → linearizable; transactional extension: serializable → strict serializable. **Quorum W+R>N guarantees read/write quorum *intersection* (a read sees a replica with the latest acked write) but is NOT linearizable** — no total order; concurrent writes, read-repair races, and sloppy quorum break it, so you need version reconciliation. Linearizable single-key needs consensus (Raft/Paxos) per shard. CAP cut: linearizability + total availability under partition is impossible; **causal is the strongest model that stays available** under partition. Each rung up costs coordination = latency + availability.
