# Work Distribution

The problem of dividing a workload across a fleet of workers so that no worker starves (idle), none is overloaded, and no work is performed redundantly. This is the central challenge in any distributed processing system — crawlers, MapReduce, task queues, stream processors.

## How it works

### Partition-based distribution
Assign ownership of work partitions to workers using [[theory/consistent-hashing]]:
1. Hash the work item's key (e.g., hostname for crawlers, user ID for request routing).
2. Map hash to a partition on the ring.
3. The worker owning that partition processes the item.

### Queue-based distribution
Use a central queue (or partitioned queues) with workers pulling work:
1. Work items enter a queue.
2. Workers pull items (competing consumers).
3. Manager tracks progress, reassigns on failure.

### Hybrid (most production systems)
Combine both: partition work into queues by key, then use pull-based consumption within each partition. This gives locality (same worker handles related items) plus backpressure (workers only pull what they can handle).

## Key points
- **Starvation avoidance**: Workers must always have work available if work exists. Queue-based systems handle this naturally; partition-based systems need rebalancing.
- **Overload prevention**: Backpressure signals (queue depth, worker lag) must throttle producers or redistribute partitions.
- **Redundancy avoidance**: Exactly-once or at-least-once semantics. Use dedup (bloom filters, idempotency keys) or lease-based ownership with timeouts.
- **Rebalancing**: When workers join/leave, work must redistribute with minimal disruption. Consistent hashing minimizes data movement.
- **Progress guarantees**: The system must make steady progress. Poison messages, hot partitions, or slow workers can stall the pipeline.

## Interview angle

> "Work distribution is about three invariants: no starvation, no overload, no redundancy. Partition by a natural key using consistent hashing for locality, use queues within partitions for backpressure, and dedup at the boundary to prevent duplicate processing."

## Connections
- [[system-design-concepts/web-crawler]] — primary example: distributing URL fetches across a crawler fleet
- [[theory/consistent-hashing]] — the standard technique for partition assignment with minimal rebalancing
- [[system-design-concepts/preemption-economics]] — the admission side: *when* queued work is allowed to start, and why it isn't preemptively interleaved

## Sources
- [[sources/docs/web-crawler-system-design]] — both crawler designs solve this as their central challenge
