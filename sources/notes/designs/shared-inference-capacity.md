# Shared inference capacity

Anonymized session notes.

## Prioritization Scheme

- Started from Linux niceness-style static priorities keyed by service identity (known via mTLS).
- Hard caps of 600/400 rejected since they waste slots; approximate 60/40 via weighted random selection from waiting queues is acceptable.
- Final model: ordered tiers, weighted sharing within each tier; a scheduler picks waiting requests when slots free up.
    - Extensible if an acquisition adds a use case sharing 50/50 with billing

## Scaling to Multiple Nodes

- Pending queues are bounded by request timeouts, so memory is small; multiple nodes are needed mainly for availability and deploys.
- Proposed splitting connection-holding gateways from workers, with idempotent request IDs for retries after dropped connections.
- Central Redis counter adds 2 to 5 ms per request; alternative is splitting quota per worker, which works if load balancing avoids hot spots.
