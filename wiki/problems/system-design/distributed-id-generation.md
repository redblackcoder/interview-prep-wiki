# Distributed ID generation

## Problem

Let many workers generate globally unique identifiers at high throughput, including across restarts and clock changes.

## Example

A worker restarts in the same clock tick and reuses its sequence counter. Unless worker identity or durable state distinguishes the two runs, an earlier ID can be issued again.

## Recorded approach

The design moved from a per-millisecond counter to worker IDs with leases. It considered clock rollback, restart state and the cost of persisting a last-issued ID.

[Anonymized candidate design notes](../../../sources/notes/designs/distributed-id-generation.md)
