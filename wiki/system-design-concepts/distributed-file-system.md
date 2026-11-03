# Distributed File System (Extent-Based Blob Storage)

Reference design for "build the storage layer yourself": a single-DC file system for PB of immutable files, sized from KB to TB each, read-heavy (1000s of reads/s against 100s of writes/s), with directories on top. GFS/HDFS, Facebook Haystack/f4, Azure Storage's stream layer and Colossus are all variations of it. The byte layout is the easy part. The cruxes are **metadata scale**, **commit visibility**, and **repair after node failure**.

## The one-sentence mental model

> **Metadata decides where, clients move bytes, and nothing is visible until one atomic commit. Objects are packed as extents (storage file, offset, length) into large append-only storage files, every extent has 3 rack-diverse copies inside a constrained copyset, and durability comes from how fast a dead node's extents are re-replicated.**

## Components
| Component | Job | Notes |
| --- | --- | --- |
| **Client SDK** | splits a put into extent writes and a get into extent reads, in parallel; verifies checksums; stitches results | talks to metadata for *where*, to storage nodes for *bytes* |
| **Metadata service** (stateless) + **metadata store** (sharded, transactional) | namespace, file → extents, extent → replicas, reverse index, capacity view | control path only; caches file → extent interval maps for reads |
| **Storage nodes** | own disks; keep a few open **append-only storage files** per disk; seal when full | node-local map storage file → disk path; checksums per block; background scrubber |
| **Repair / placement manager** | heartbeats, liveness, re-replication queue, rebalancing, compaction | [[system-design-concepts/replica-repair-and-re-replication]] |
| **GC** | reclaims PENDING uploads past TTL, deleted files' extents, holes | [[system-design-concepts/immutable-object-commit]] |

## Data model
- **Namespace:** `(parent_dir, name) → file_id | dir marker`, sorted. `list(dir)` is a prefix/range scan with a page token. "Arbitrary number of files per directory" means one directory can be huge, so **range-partition on (dir, name)** and split hot directories by name. Hash-by-dir would pin a huge directory to one shard ([[system-design-concepts/hash-vs-range-partitioning]]).
- **File:** `file_id → {state: PENDING|COMMITTED|DELETED, size, upload_id, created_at}`.
- **Extents:** `(file_id, logical_offset) → [(storage_file, target_offset, length, crc) × 3]`. It's an interval map, so a read of (offset, length) is a floor lookup plus a walk.
- **Reverse index:** `storage_file → [(file_id, extent, replica#)]`, written **in the same transaction** as the forward rows. Repair needs it ([[system-design-concepts/replica-repair-and-re-replication]]).
- **Storage file:** `storage_file → node`, plus `capacity_left`, `sealed`.

Keep a file's namespace row and its extent rows on the **same metadata shard** (shard by parent directory range), so commit is a single-shard transaction, not 2PC.

## The addressing unit: 4 KB inside large storage files
- Storage files are large (GBs). Extents are **addressed by offset at 4 KB alignment**. Metadata is **per extent, not per block**, so a 1 TB file placed into a few ~GB-sized extents is a few hundred rows, not 250M.
- Small files: a 5 KB file uses two 4 KB blocks. The waste is ~2 KB per file on average. At 1B small files that's ~2 TB, which is noise at PB scale.
- **Anti-pattern to avoid:** making a large chunk (64–128 MB) the *minimum addressable unit*. Small files then waste the tail, or need a sub-chunk reservation scheme anyway. GFS's 64 MB chunk works because GFS files are large. Haystack exists because Facebook photos aren't.
- **Metadata scale is driven by file count, not bytes.** 10 PB in ~1 GB extents ≈ 10M extents × 3 replicas × ~100 B ≈ 3 GB. But 1B small files × ~200 B of namespace + extent rows ≈ 200 GB, which is why the metadata store is sharded, not a single in-memory master (GFS's scaling wall).

## Write path (put)
1. `create(dir, name, size)` → metadata checks the name is free, inserts file `PENDING` with an `upload_id`, splits the size into extents by available capacity, and places 3 replicas per extent **within one copyset** ([[system-design-concepts/copysets]]). It returns signed, expiring **write handles**.
2. The client streams each extent, in parallel across extents. Either it sends to the first replica, which pipelines to the next two (the GFS chain: each byte crosses the client NIC once), or it fans out ×3 directly.
3. Each storage node **appends**, fsyncs, and returns `(storage_file, offset, crc)`. If nodes assign offsets on append, holes only come from real failures. If metadata pre-reserves offsets, every abandoned reservation leaves a hole.
4. `commit(upload_id, acks)` → one metadata transaction inserts extents + reverse index, flips to `COMMITTED`, and makes the name visible. Retrying commit with the same `upload_id` is idempotent ([[system-design-concepts/immutable-object-commit]]).

## Read path (get)
1. `get(path, offset, length)` → metadata resolves path → file_id → the extents overlapping the range (served from the cached interval map; files are immutable, so the cache never goes stale except on delete). It returns **read handles listing all 3 replicas**.
2. The client picks the nearest or least-loaded replica per extent, and sends a **hedged read** to a second replica past ~p95.
3. It verifies the block checksums. On mismatch it reads another replica and reports the corruption, which triggers a repair of that replica.

## Sizing (one number per parameter)
- **Storage:** 10 PB × 3 = 30 PB raw; at ~300 TB/node that's **~100 storage nodes**. With RS(6,3) for cold data: 15 PB raw ([[system-design-concepts/replication-vs-erasure-coding]]).
- **Read bandwidth:** 1000 reads/s × ~64 MB ≈ **64 GB/s ≈ 512 Gbps** fleet-wide ≈ 5 Gbps/node over 100 nodes. Reads are bandwidth-bound, so spread them across all 3 replicas.
- **Metadata:** ~3 GB of extents for large files, ~200 GB if 1B small files. It's the file count that forces sharding.
- **Repair:** a 300 TB node rebuilt from 100 peers at ~200 MB/s each ≈ **4 h MTTR**. That's the number that sets durability ([[theory/durability-math]]).

## Key points
- **Control path vs data path.** The metadata service never touches bytes. Handles carry placement to the client.
- **Immutability is the gift.** No overwrite means no consistency protocol between replicas for the same bytes. The only consistency question is *visibility* (the commit), and the interval-map cache never invalidates except on delete.
- **Per-extent placement** maximizes capacity use and spreads load, but repair needs the reverse index, and correlated failure needs copysets.
- **Holes are inevitable** in append-only storage files (failed uploads, deletes). A compactor copies live extents into new storage files, and each move is a metadata update through the same path as repair.
- **Single DC ⇒ rack/power-domain-aware placement.** 3 AZs is the multi-region/multi-AZ bonus, not the baseline.

## Interview angle
> "Metadata service for *where*, storage nodes for *bytes*, and the client moves data directly. I pack files as extents (storage file, offset, length) into large append-only storage files at 4 KB alignment, so metadata is per extent and small files don't waste a chunk. A put creates a PENDING entry, streams extents to 3 rack-diverse replicas in a copyset, and one atomic commit makes it visible. Heartbeats detect dead nodes, a reverse index finds their extents, and re-replication runs fewest-copies-first and rate-limited, because MTTR is what sets durability. Cold immutable data gets erasure coded for about half the raw bytes."

## Connections
- [[system-design-concepts/immutable-object-commit]]: PENDING → COMMITTED, idempotent commit, orphan and delete GC
- [[system-design-concepts/replica-repair-and-re-replication]]: heartbeats, reverse index, repair priority and throttling
- [[system-design-concepts/copysets]]: constrained placement against correlated failure
- [[system-design-concepts/replication-vs-erasure-coding]]: 3x vs RS(6,3); where to use each
- [[theory/durability-math]]: why MTTR, not RF, sets the nines
- [[system-design-concepts/hash-vs-range-partitioning]]: range-partitioned namespace for `list` and huge directories
- [[system-design-concepts/lease-and-fencing]]: expiring write handles and offset reservations are leases
- [[system-design-concepts/database-write-capacity]]: metadata-store write load (commits, repair churn)
- [[system-design-concepts/video-delivery-read-path]]: another read-bandwidth-bound blob read path, with a CDN in front

## Sources
- [[sources/docs/design-distributed-file-system-interview]]: the architecture round: the design and the per-extent replication discussion
- Background, not ingested: GFS (SOSP 2003), Haystack (OSDI 2010), Windows Azure Storage (SOSP 2011), f4 (OSDI 2014)
