# Price-triggered purchases

## Problem

Buy an item before the customer's deadline if a seller quotes a price within the limit. Sellers expose current quotes and purchase calls, and the price may change between calls.

## Example

A ten-hour order becomes affordable in its first hour and expensive again by the last half-hour. Checking only near expiry misses the opportunity.

## Recorded approach

Persist orders, queue due work by book, and use each order ID as the seller idempotency key. Hourly check buckets were proposed after a long order could go unchecked.

### Proposed Design

- Ordering service writes pending orders to a DB; an allocator queries due orders and pushes to Kafka partitioned by book name
- Allocator dedupes by book and batches about 100 orders per message; fulfillment workers buy and update the DB
- DB marker with increasing version plus 5 minute hold timeout prevents duplicate queueing and stale overwrites
    - Marker written to DB first, then queue
- Order ID used as idempotency key in seller buy API so retries never double-buy

### Gaps Raised and Production Readiness

- Interviewer flagged a 10 hour order would sit unchecked until the last 30 minutes
- Fix proposed: denormalize each order into hourly check buckets the allocator reads from
- Launch plan: gradual rollout, queue and DB monitoring, queue-based autoscaling, allocator leader/standby, DB backups, seller rate limiting

[Anonymized session notes](../../../sources/notes/designs/price-triggered-purchases.md)
