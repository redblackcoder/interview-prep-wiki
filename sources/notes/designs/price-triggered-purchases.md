# Price-triggered purchases

Anonymized session notes.

## Proposed Design

- Ordering service writes pending orders to a DB; an allocator queries due orders and pushes to Kafka partitioned by book name
- Allocator dedupes by book and batches about 100 orders per message; fulfillment workers buy and update the DB
- DB marker with increasing version plus 5 minute hold timeout prevents duplicate queueing and stale overwrites
    - Marker written to DB first, then queue
- Order ID used as idempotency key in seller buy API so retries never double-buy

## Gaps Raised and Production Readiness

- Interviewer flagged a 10 hour order would sit unchecked until the last 30 minutes
- Fix proposed: denormalize each order into hourly check buckets the allocator reads from
- Launch plan: gradual rollout, queue and DB monitoring, queue-based autoscaling, allocator leader/standby, DB backups, seller rate limiting
