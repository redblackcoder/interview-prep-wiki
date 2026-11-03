# Replica Repair and Re-Replication

What a replicated storage system does **after** a node dies. Writing 3 copies is only the setup. Durability comes from noticing the loss and restoring the replica count before the other copies die too. "What if that node is unavailable?" is asking about this, not about write-time replication.

## The one-sentence mental model

> **Detect fast but repair patiently: heartbeats mark a node suspect in seconds (stop placing on it, read around it), re-replication starts after a longer timeout, fewest-surviving-copies first, rate-limited, and rebuilt in parallel from many peers, because repair speed (MTTR) is what sets the nines.**

## How it works
1. **Heartbeats.** Every few seconds a storage node reports capacity, load, and disk health. Periodically (or at startup) it also sends an **inventory report**: the storage files and extents it holds.
2. **Two timeouts.**
   - *Suspect* after a few missed heartbeats: stop placing new extents there and route reads to other replicas.
   - *Dead* after a longer timeout (HDFS defaults to ~10 min): start re-replicating. The gap stops a reboot or a brief network blip from triggering a PB-scale repair storm.
3. **Find what to repair.** The reverse index `storage_file → [(file_id, extent, replica#)]` gives the dead node's extents directly. Without it, you scan the whole file table. The alternative is GFS's approach: don't persist locations, and rebuild them from inventory reports (whatever nobody reports is under-replicated).
4. **Prioritize.** A priority queue ordered by surviving copies: 1 left before 2 left. Extents on hot files go first within a tier.
5. **Copy node → node.** Pick a surviving source and a target that satisfies placement (rack-diverse, same copyset). Bytes never pass through the metadata service.
6. **Fenced metadata swap.** Replace the dead replica only if the replica set is unchanged: `UPDATE extent_replica … WHERE replica_set_version=:v`. A concurrent compaction or a second repair of the same extent then can't clobber the result ([[system-design-concepts/lease-and-fencing]]).
7. **Throttle.** Cap repair bandwidth per node and globally so foreground reads keep their SLO. Raise the cap when an extent is down to its last copy.

## MTTR is set by scatter width
- Rebuild time ≈ `data on dead node / (peers rebuilding × per-peer repair rate)`.
- 300 TB from **100 peers** at 200 MB/s each ≈ 15,000 s ≈ **4 h**. From **2 peers** (mirrored pairs) it's ≈ 9 days.
- The durability formula has loss ∝ `(MTTR/yr)^(RF−1)` ([[theory/durability-math]]), so spreading a node's data across many peers is worth more than a 4th replica.
- The tension: many peers per node also means many node triples share data, which raises the chance that a correlated 3-node failure loses something. That trade-off is [[system-design-concepts/copysets]].

## Repair is a metadata write workload
- A 300 TB node at ~1 GB extents is ~300K extents. At 4 MB/extent it's 75M. Each needs a placement decision plus a fenced metadata update.
- Batch updates per target storage file. Budget repair writes against the metadata store's commit capacity ([[system-design-concepts/database-write-capacity]]).
- **Compaction** (moving live extents out of storage files with holes) and **rebalancing** (onto new, empty nodes) use the same move-plus-fenced-swap path.

## Other repair triggers
- **Bit rot:** a background scrubber re-reads blocks and verifies checksums. Client checksum failures on read also report the bad replica. Both enqueue a single-replica repair.
- **Disk (not node) failure:** same flow, scoped to the storage files on that disk.
- **Decommission:** drain a node on purpose. It's the same queue at low priority, and the node stays readable until drained.

## Key points
- Write-time replication answers "don't lose data on write." Repair answers "don't lose data over a year." Interviewers ask about the second.
- Don't let a flapping node cause repeated full repairs. Use the suspect/dead split, and cancel queued repairs if the node returns with its inventory intact.
- Repair must never starve reads, except for last-copy extents.
- Leaderless KV stores do the same job with anti-entropy (Merkle-tree diffing) instead of a central reverse index ([[system-design-concepts/anti-entropy-merkle-trees]]).

## Interview angle
> "Nodes heartbeat with capacity and inventory. A few misses mark a node suspect, so we stop placing there and read around it. A longer timeout declares it dead and starts re-replication. The reverse index lists its extents, queued fewest-copies-first. Each extent is copied peer to peer into a rack-diverse target, then swapped in metadata with a fenced write. It's rate-limited against reads, and rebuilt from many peers in parallel, because MTTR is what sets durability."

## Connections
- [[theory/durability-math]]: MTTR is the durability lever this page engineers
- [[system-design-concepts/copysets]]: scatter width vs correlated-failure risk
- [[system-design-concepts/distributed-file-system]]: the system this repairs
- [[system-design-concepts/anti-entropy-merkle-trees]]: the leaderless equivalent of inventory diffing
- [[system-design-concepts/lease-and-fencing]]: fenced replica-set swap
- [[system-design-concepts/database-write-capacity]]: repair churn as metadata write load
- [[system-design-concepts/replication-vs-erasure-coding]]: EC repair reads k fragments, which changes the repair budget

## Sources
- [[sources/docs/design-distributed-file-system-interview]]: node-loss repair: reverse index, repair churn
