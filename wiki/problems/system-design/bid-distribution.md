# Bid distribution

## Problem

Distribute frequently changing advertiser bids to independently operated serving clusters. Each cluster needs local reads and a recovery path after falling behind.

## Example

A cluster comes back after missing several update cycles. Loading a snapshot and then applying later deltas bounds the work needed to catch up.

## Recorded approach

Publish chunked bid deltas keyed by advertiser. Each cluster applies updates locally and can recover from a snapshot plus later offsets.

### Design: Distributing Bids to Ad-Serving Clusters

- Bids for about 15 million advertisers produced every five minutes; assume about 1 million change per cycle
- Rejected direct API calls due to network overhead; proposed chunked deltas on a queue consumed per cluster into a key-value store
    - Key by advertiser ID, not query (query cardinality too high)
    - Staggered updates across clusters are acceptable
- Instrumentation: enqueue time for consumer latency, Kafka-style offset lag, producer-side error telemetry
- Recovery: stateless consumers resume from offset; hourly snapshots plus replay of later deltas avoid long backlogs

[Anonymized session notes](../../../sources/notes/designs/bid-distribution.md)
