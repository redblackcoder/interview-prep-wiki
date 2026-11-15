# Replication vs Erasure Coding

Two ways to survive disk and node loss. **Replication** stores whole copies: simple, cheap to read and repair, and 3x the bytes. **Erasure coding (EC)** splits data into k data fragments plus m parity fragments, any k of which rebuild the original. That's ~1.3–1.5x the bytes with equal or better fault tolerance, but repair and degraded reads cost far more I/O. Big storage systems use both: **replicate while hot or open, erasure code once sealed and cold.**

## The one-sentence mental model

> **EC trades repair and read I/O for raw capacity. RS(6,3) survives 3 losses at 1.5x where 3-way replication survives 2 at 3x, but rebuilding one lost fragment reads 6. So you EC large, sealed, immutable, colder data and keep small, hot, or still-being-written data replicated.**

## How it compares
| | 3-way replication | RS(6,3) | RS(10,4) | Azure LRC(12,2,2) |
| --- | --- | --- | --- | --- |
| Raw bytes per logical byte | 3.0x | 1.5x | 1.4x | ~1.33x |
| Losses tolerated | 2 | 3 | 4 | 3 (and many 4-loss patterns) |
| Rebuild one lost unit reads | 1 copy | 6 fragments | 10 fragments | 6 fragments (local group) |
| Normal read | any 1 replica | the fragment(s) holding the range (systematic code) | same | same |
| Degraded read (a fragment is down) | another replica | read k, decode | read k, decode | read the local group |
| Write | 3 appends | encode + k+m writes | same | same |

- **For a 10 PB lakehouse:** 3x = 30 PB raw, while RS(6,3) = 15 PB raw. That's 15 PB of disks, power, and racks saved.
- **Real systems:** Facebook's f4 moved warm BLOBs off Haystack and cut the effective replication factor from 3.6 to 2.8 or 2.1. Azure Storage erasure codes sealed extents with LRC. HDFS supports EC policies on cold directories.

## How it works in a DFS
- **Encode at the storage-file level, not per user file.** Striping a 4 KB file into 6 fragments is absurd. Instead, once a large append-only storage file is **sealed**, a background job encodes it into k+m fragments on k+m different nodes or racks, then drops the replicas.
- **Immutability makes this easy.** No overwrites means no read-modify-write of parity. Sealing is a natural trigger.
- **Metadata change:** an extent's location becomes `(stripe_id, fragment, offset)` instead of 3 replica addresses. Reads map offsets to the data fragment. Repair rebuilds fragments.
- **Placement:** k+m fragments need k+m failure domains. RS(6,3) needs 9 racks to survive the loss of 3 whole racks.

## Key points
- **Repair amplification is the cost:** one lost 300 TB node under RS(6,3) means reading ~1.8 PB to rebuild. Budget repair bandwidth accordingly ([replica repair and re replication](replica-repair-and-re-replication.md)). LRC exists to cut this.
- **Tail latency:** a degraded read waits on k nodes. Hedge, or keep hot data replicated.
- **Small objects:** EC only pays off at stripe granularity, so pack first ([distributed file system](distributed-file-system.md)), then encode the packed volume.
- **Durability:** EC with m=3 or m=4 usually beats 3-way replication on independent failures. Correlated failure still depends on placement ([copysets](copysets.md)).
- **Cost framing lands with cost-focused interviewers.** For a cloud-cost audience, "immutable files → EC cold data → half the raw bytes" is the point to make.

## Interview angle
> "I write replicated, 3 copies, so the write path and hot reads stay simple. When a storage file seals, I erasure code it, say RS(6,3). That still survives 3 losses but at 1.5x instead of 3x, which halves raw capacity at PB scale. The cost is repair and degraded reads: rebuilding a fragment reads 6. So I keep hot data replicated, throttle EC repair, and consider LRC if repair bandwidth becomes the bottleneck."

## Connections
- [durability math](../theory/durability-math.md): RF math generalizes to "any m of k+m can fail within the repair window"
- [replica repair and re replication](replica-repair-and-re-replication.md): EC changes the repair budget (k-fold reads)
- [distributed file system](distributed-file-system.md): seal-then-encode fits append-only storage files
- [copysets](copysets.md): fragment placement across failure domains
- [cloud database cost model](cloud-database-cost-model.md): raw-capacity cost is the lever EC pulls

## Sources
- [lakehouse file storage](../problems/system-design/lakehouse-file-storage.md): erasure coding as the cost lever for sealed, immutable files
- Background, not ingested: f4 (OSDI 2014); Huang et al., "Erasure Coding in Windows Azure Storage" (USENIX ATC 2012)
