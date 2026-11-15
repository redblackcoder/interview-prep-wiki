# Distributed persistent key-value store

Provide durable point reads and partition-local range scans across storage nodes.

## Recorded approach

Route requests through stateless coordinators and replicate across failure domains. The exercise ended at the choice between direct quorum writes and a leader per shard.

[Anonymized design notes](https://github.com/redblackcoder/interview-prep-raw/blob/master/docs/designs/distributed-persistent-key-value-store.md)
