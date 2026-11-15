# Distributed ID generation

Allocate unique identifiers from multiple machines without a central write for every ID.

## Recorded approach

The design moved from a per-millisecond counter to worker IDs with leases. It considered clock rollback, restart state and the cost of persisting a last-issued ID.

[Anonymized candidate design notes](https://github.com/redblackcoder/interview-prep-raw/blob/master/docs/designs/distributed-id-generation.md)
