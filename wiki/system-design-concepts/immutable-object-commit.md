# Immutable Object Commit (PENDING → COMMITTED)

How a storage system makes a multi-part write of an immutable object appear **all at once or not at all**, when the bytes land on many nodes over minutes and any participant can crash midway. The answer is to separate *writing bytes* from *publishing the name*. Bytes go anywhere, unreferenced. One atomic metadata write makes them reachable. S3 multipart upload, HDFS files under construction, and lakehouse table commits (Iceberg/Delta manifests) all use this shape.

## The one-sentence mental model

> **Bytes are cheap and invisible until a single conditional metadata write publishes them. So every crash before that write leaves only garbage (never a half-visible file), and every crash after it is fixed by an idempotent retry.**

## How it works
1. **Create:** insert the file row `PENDING` with an `upload_id` and `created_at`. It is invisible to `get`/`list`.
2. **Write:** the client writes extents to storage nodes, and each ack returns `(storage_file, offset, crc)`. Nothing in the namespace points at them yet.
3. **Commit:** one transaction on the metadata shard:
   - `UPDATE file SET state='COMMITTED' WHERE upload_id=:u AND state='PENDING'`
   - insert extent rows and reverse-index rows
   - insert the namespace entry **only if the name is free** (put-if-absent)
4. **Retry is idempotent:** a second commit with the same `upload_id` finds `COMMITTED` and returns success. A different `upload_id` for the same name gets a conflict.
5. **GC:** a sweeper deletes `PENDING` rows older than the upload TTL and frees their extents (holes for compaction). It's the same job as S3's "abort incomplete multipart uploads after N days" lifecycle rule.

## Crash table
| Crash point | What's left | Recovery |
| --- | --- | --- |
| Before create | nothing | none |
| After create, before any bytes | PENDING row, maybe capacity reservations | TTL sweeper; reservations expire as leases |
| Mid-upload | some extents written, unreferenced | client resumes with the same `upload_id`, or the TTL sweeper frees them → holes → compaction |
| After all acks, before commit | all bytes, file still PENDING | client retries commit (idempotent) |
| After commit, before the response | COMMITTED | client retries commit → success, same `upload_id` |

## Delete
- `delete(path)` removes or tombstones the namespace entry in one write, so the file is invisible immediately.
- The extents are queued for GC **after a grace period ≥ the read-handle TTL**, so readers already holding handles can finish.

## Key points
- **The commit is the only linearization point.** State "a file becomes visible exactly when commit returns" explicitly; having the ack and the pruner is not enough if the design never says *when* readers can see the file.
- **Storage-side GC must be driven by metadata.** A storage node can't tell "orphan" from "upload still in progress". Reclaim only extents that are unreferenced **and** older than the upload TTL. Otherwise GC races a slow upload and deletes live bytes.
- **Concurrent puts to one path:** because files are immutable, the natural rule is put-if-absent, so the first commit wins. Last-writer-wins would need versioning and makes open readers' handles ambiguous.
- **Two-phase at the application level, not 2PC.** Participants (storage nodes) never vote. They just hold bytes that are harmless if never referenced. That's why no coordinator log is needed.
- The same move shows up in lakehouse tables: data files are written first, then one atomic swap of the metadata pointer commits the snapshot.

## Interview angle
> "The file row starts PENDING and invisible. The client streams extents anywhere, and one conditional metadata transaction flips it to COMMITTED and inserts the name if absent. That commit is the moment it becomes visible. Any crash before it leaves unreferenced bytes that a TTL sweeper reclaims. Any crash after it is fixed by retrying commit with the same upload id, which is idempotent."

## Connections
- [distributed file system](distributed-file-system.md): where this sits in the put path
- [transactional outbox](transactional-outbox.md): sibling "one local atomic write, everything else follows" pattern
- [exactly once semantics](exactly-once-semantics.md): idempotent commit keyed by `upload_id` is the effectively-once close
- [lease and fencing](lease-and-fencing.md): write handles and capacity reservations expire as leases
- [copy on write vs mvcc](../theory/copy-on-write-vs-mvcc.md): publish-by-pointer-swap is copy-on-write at the object level

## Sources
- [lakehouse file storage](../problems/system-design/lakehouse-file-storage.md): commit visibility and the crash-mid-upload question
