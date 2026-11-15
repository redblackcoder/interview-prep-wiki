# Stream backlog diagnosis

## Problem

Diagnose a partitioned event queue whose end-to-end delay grows while consumers appear healthy. Determine where demand exceeds processing capacity.

## Example

If most partitions are current and one is hours behind, adding consumers may leave the bottleneck untouched. Compare key distribution and drain rate on that partition.

## Recorded approach

Investigate hot partitions and retrying sources. The recorded proposals were key salting, incorporating session identity, and throttling the offending source.

### Kafka Queue Latency Scenario

- Scenario: enqueue latency grew from hours to days, high offsets, 128 partitions, no producer change, consumers healthy.
- The candidate traced it to hot partitions from a user ID key, likely one user (agents or retrying workflows) flooding events.
- Proposed fixes: repartition with salt or user ID plus session ID; hotfix to drop that user's events in consumer and producer.

[Anonymized session notes](../../../sources/notes/designs/stream-backlog-diagnosis.md)
