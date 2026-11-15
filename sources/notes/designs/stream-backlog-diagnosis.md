# Stream backlog diagnosis

Anonymized session notes.

## Kafka Queue Latency Scenario

- Scenario: enqueue latency grew from hours to days, high offsets, 128 partitions, no producer change, consumers healthy.
- The candidate traced it to hot partitions from a user ID key, likely one user (agents or retrying workflows) flooding events.
- Proposed fixes: repartition with salt or user ID plus session ID; hotfix to drop that user's events in consumer and producer.
