# Block-aligned range cache

Serve arbitrary byte ranges using a local cache of fixed-size remote blocks.

## Recorded approach

The sketch splits reads by chunk, submits read tasks and maintains per-chunk semaphores around cache fills. It contains unresolved types and offsets.

[Original Java sketch](https://github.com/redblackcoder/interview-prep-raw/blob/master/code/exercise-notes/block-aligned-range-cache.md)
