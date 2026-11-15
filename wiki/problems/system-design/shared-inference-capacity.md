# Shared inference capacity

## Problem

Allocate a fixed number of concurrent model calls among features with different priorities and target shares. Calls are latency-sensitive and idle capacity should remain usable.

## Example

Two same-tier features target a 60/40 split. If the second has no waiting work, a hard 60 percent cap strands usable slots even while the first has requests waiting.

## Recorded approach

Use priority tiers and weighted sharing among waiting calls. Compare a shared counter with per-worker allocations and bound pending requests by timeout.

### Prioritization Scheme

- Started from Linux niceness-style static priorities keyed by service identity (known via mTLS).
- Hard caps of 600/400 rejected since they waste slots; approximate 60/40 via weighted random selection from waiting queues is acceptable.
- Final model: ordered tiers, weighted sharing within each tier; a scheduler picks waiting requests when slots free up.
    - Extensible if an acquisition adds a use case sharing 50/50 with billing

### Scaling to Multiple Nodes

- Pending queues are bounded by request timeouts, so memory is small; multiple nodes are needed mainly for availability and deploys.
- Proposed splitting connection-holding gateways from workers, with idempotent request IDs for retries after dropped connections.
- Central Redis counter adds 2 to 5 ms per request; alternative is splitting quota per worker, which works if load balancing avoids hot spots.

[Anonymized session notes](../../../sources/notes/designs/shared-inference-capacity.md)
