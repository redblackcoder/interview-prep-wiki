# Block-aligned range cache

## Problem

Read a byte interval from a remote file through a local cache of fixed-size chunks. Requests can start or end inside a chunk, and concurrent requests may need the same missing chunk.

## Example

With 1,000-byte chunks, [900,1100) needs the last 100 bytes of chunk 0 and the first 100 of chunk 1. A second reader of chunk 1 should be able to reuse the first fetch. The saved sketch does not finish the offset and cache-fill logic.

## Recorded approach

The sketch splits reads by chunk, submits read tasks and maintains per-chunk semaphores around cache fills. It contains unresolved types and offsets.

[Original Java sketch](../../../sources/notes/coding/block-aligned-range-cache.md)
