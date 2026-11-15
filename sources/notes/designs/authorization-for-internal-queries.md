# Authorization for internal queries

Anonymized session notes.

## Hypothetical: GraphQL Authorization Gap

- GraphQL service-to-service RPCs bypass Envoy ingress, skipping per-route entitlement checks and rate limiting
- The proposed approach used sidecar enforcement; the interviewer noted trade-offs: latency, memory, cache misses, policy skew
    - Mitigation: resolve token and groups once near the boundary to enrich request context
