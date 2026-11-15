# Durable single-node key-value store

Anonymized session notes.

## Base Design: WAL Plus In-Memory Map

- Puts append key=value to a write-ahead log via a FileChannel, forced to disk, then update a ConcurrentHashMap.
    - Log first, then memory, so a failed write never leaves a phantom value in memory
- Writes are serialized with a ReentrantReadWriteLock to keep WAL order matching memory order; gets are unlocked.
- Recovery replays the whole WAL on startup, so later writes overwrite earlier ones; assumes no '=' in keys or values.
- Suggested growth fix: segment files compacted by a background thread, SSTable-style levels with dedup.

## Performance Under High Concurrency

- Bottleneck identified: all writes are single-threaded and each one forces an expensive disk flush.
- Proposed group commit: buffer writes (4 KB threshold) and flush in batch, with putters waiting on a flush signal.
    - Needs a thread-safe buffer and a re-check of size after taking the flush lock to avoid double flush
- The candidate noted values could vanish after crash if visible before flush; the candidate proposed flush-ID versioning for visibility.
- The candidate flagged low-concurrency waits with no flush trigger; fix is a background timer flushing every few ms.
