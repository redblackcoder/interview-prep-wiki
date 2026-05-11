# Consistent Hashing

A hashing technique that maps both keys and nodes onto a ring, so that adding or removing a node only reassigns a small fraction of keys (O(K/N) on average). Used whenever you need stable partition assignment across a dynamic fleet.

## How it works

1. Hash both keys and nodes onto the same circular space (0 to 2^m - 1).
2. Each key is assigned to the first node encountered clockwise on the ring.
3. When a node is added: it takes ownership of keys between itself and its predecessor — only those keys move.
4. When a node is removed: its keys move to the next node clockwise — only those keys move.

### Virtual nodes
Each physical node maps to multiple positions on the ring (virtual nodes). This smooths out load distribution and prevents hotspots from uneven hash placement.

## Key points
- **Minimal disruption**: Adding/removing N nodes only moves O(K/N) keys, vs. O(K) with modulo hashing.
- **Virtual nodes are essential** in practice — without them, load variance is unacceptable.
- **Not just for caches**: Used for partition assignment in databases (DynamoDB, Cassandra), task distribution (crawler shards), and load balancing.
- **Rebalancing trade-off**: In a web crawler, instead of moving already-crawled content on rebalance, you can defer redistribution to the next re-crawl cycle.

## Interview angle

> "Consistent hashing maps keys and nodes onto a ring so that node changes only affect neighboring keys. With virtual nodes for load balance, it's the standard technique for partition assignment in distributed systems — from DynamoDB to web crawler shard managers."

## Connections
- [[system-design-concepts/work-distribution]] — consistent hashing is the default partition assignment strategy
- [[system-design-concepts/web-crawler]] — used to assign host ownership to crawler shards

## Sources
- [[sources/docs/web-crawler-system-design]] — consistent hashing for host-to-shard assignment with minimal content movement on rebalance
