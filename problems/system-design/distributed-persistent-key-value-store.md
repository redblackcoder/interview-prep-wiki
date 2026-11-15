# Distributed persistent key-value store

Provide durable point reads and partition-local range scans across storage nodes.

## Solution summary

Route by partition key, order by sort key inside a shard, and replicate shard updates.

[Full solution in interview-prep-raw](https://github.com/redblackcoder/interview-prep-raw/blob/master/docs/designs/distributed-persistent-key-value-store.md)
