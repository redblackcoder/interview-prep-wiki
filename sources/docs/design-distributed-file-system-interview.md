---
source: interview session (raw transcript not published)
type: doc
date_extracted: 2026-09
topic: system-design-concepts
---

# Design a Distributed File System for a Lakehouse — Architecture Round

Distilled from an architecture round (~45 min). Prompt: **"build a scalable, reliable file system for all customers' lakehouse data, without using existing cloud/blob storage."** Files are immutable (no modify, no append). Directories are mutable. Scope was single region and single DC, with multi-region as a bonus.

## Key Ideas

- **Control path vs data path.** The metadata service decides placement and returns handles. Bytes go client ↔ storage nodes directly and in parallel. This is the central GFS/HDFS idea.
- **Two-level mapping.** (1) Logical path → [storage file, source offset, target offset, length]. (2) Storage file → storage node. The storage node keeps its own storage file → disk/offset mapping. Metadata grows with the number of *extents*, not 4 KB blocks. The metadata service caches a range structure to translate (offset, length) into extents on read.
- **Pack into large storage files.** One OS file per 4 KB segment would drown the node in inode metadata. Segments are appended into a few open storage files per node, sequentially, with no random seeks.
- **Replication is per extent.** The metadata service places 3 copies of each extent wherever capacity fits best. This is valid and uses capacity best. Its costs are covered under per-extent replication below.
- **Capacity publication.** Storage nodes publish open storage files and remaining capacity. They open new files at ~50% full, and new nodes or disks are the scale-out lever. *Has to close:* how a reservation is made atomic, who picks the offset, and what happens to a reservation that is never used.
- **Commit visibility.** Create the file entry **PENDING**, invisible to `get`/`list`. The client writes the extents, with checksums. Once every extent is acknowledged, **one atomic metadata write flips it to COMMITTED**. A GC job reclaims PENDING files older than a TTL. An ack and a pruner are not enough on their own: say *when a file becomes visible*.
- **Node failure.** Storage nodes heartbeat with capacity and extent inventory. Missed heartbeats mark a node dead. Under-replicated extents are re-replicated, fewest surviving copies first, rate-limited against foreground reads. Placement is rack-aware inside the single DC.
- **Holes and compaction.** Failed or abandoned writes leave holes in append-only storage files. A background compactor copies live extents into new storage files and frees the old ones. It must update extent metadata, so it uses the same path as repair.
- **One-line answers to have ready.**
  - Directories/`list`: a sorted KV keyed by (dir, name) with a prefix scan.
  - `delete`: a tombstone in the namespace plus async extent GC.
  - Streaming TB uploads: the client streams extents as the handles are issued, not one blob.
- **The cost angle.** Immutable files make **erasure coding** easy, e.g. Reed-Solomon 6+3 at ~1.5x overhead instead of 3x, at least for cold data.

## My Understanding

- The cruxes of a DFS are: chunk/extent unit, metadata scale, replication and repair, commit visibility. Put a sizing sentence on each, then draw.
- Walk one put and one get end to end before zooming into the byte layout.
- Single DC means rack/power-domain-aware placement, not 3 AZs. Multi-AZ belongs to the multi-region bonus.
- "What if the node is down?" asks about repair, not write-time copies. Answer detection → re-replication → placement constraint in one breath.
- **The 4 KB unit is valid.** A large chunk (say 64–128 MB) as the minimum *addressable* unit wastes the unused tail, and you can't reserve inside it for many small files. With metadata per extent (storage file, offset, length), one entry spans many 4 KB blocks, so 4 KB is only the addressing and alignment granularity inside large packed storage files — a Haystack/extent-style design. "Pack small files into large chunks" collapses into the same design anyway: packing only works if you address by offset inside the large file.
- **Per-extent replication, and what it costs.** Placing each extent's 3 copies independently by best capacity fit (rather than replicating a whole storage file) is valid, but it makes repair the hardest part. The four follow-ups to have ready:
  1. **Reverse index for repair.** The DB maps file → extents and storage file → node. Repair needs the reverse: storage file (or node) → [(file, extent, replica#)]. Write it in the same transaction as the forward mapping, *or* rebuild it from storage-node inventory reports in heartbeats, the way GFS rebuilds chunk locations from chunkserver reports.
  2. **Repair churn is a metadata write workload.** A ~300 TB node holds millions of extents, and each needs a new placement plus a metadata update. Batch updates per target storage file. Rate-limit repair behind foreground reads. Repair extents with 1 surviving copy before those with 2.
  3. **Copysets (correlated failure).** If each extent picks 3 nodes independently, almost every node triple shares some extent, so *any* 3-node simultaneous loss (rack power, bad rollout) almost surely loses data (Cidon et al., "Copysets", 2013). The fix: restrict placement to a limited set of rack-diverse node triples and best-fit *within* a copyset. This is also the single-DC rack-awareness answer.
  4. **Split once, place three times.** If each replica is split by its own capacity fit, replica A might be 1 extent while replica B is 2. Reads cope, but checksums and repair are simpler with one split per logical range and 3 same-length copies.
- **30-second answer to "what if that node is unavailable?":**
  > "I replicate per extent for placement flexibility. The cost is repair, so I keep a reverse index from storage file to extents. Missed heartbeats trigger re-replication, prioritized by surviving copies and rate-limited. I constrain placement to rack-diverse copysets so a correlated 3-node failure doesn't lose data."

## Open Questions

- **Who picks the offset?** If the metadata service pre-reserves offset ranges, you get the holes problem whenever a reservation goes unused. If the storage node appends and returns the offset, holes only come from real write failures, and the metadata commit happens afterward. Both work. The difference is how much compaction you need.

## Connections
- Durability of 3 replicas vs repair time (MTTR drives P(loss)): [[wiki/theory/durability-math]], [[wiki/theory/durability-rpo-rto]].
- Background repair by scanning vs inventory diffing: [[wiki/system-design-concepts/anti-entropy-merkle-trees]].
- Reservation that "dies" if not followed up is a lease: [[wiki/system-design-concepts/lease-and-fencing]].
- Metadata DB write load from repair churn: [[wiki/system-design-concepts/database-write-capacity]].
- Namespace as sorted KV for `list` (prefix scan): [[wiki/system-design-concepts/hash-vs-range-partitioning]].

## Concepts flagged for wiki
- **Extent-based blob storage / DFS reference design.** *(new: [[wiki/system-design-concepts/distributed-file-system]])*
- **Copysets.** *(new: [[wiki/system-design-concepts/copysets]])*
- **Re-replication and repair.** *(new: [[wiki/system-design-concepts/replica-repair-and-re-replication]])*
- **Commit visibility for immutable objects.** *(new: [[wiki/system-design-concepts/immutable-object-commit]])*
- **Replication vs erasure coding.** *(new: [[wiki/system-design-concepts/replication-vs-erasure-coding]])*
