# Authorization for internal queries

## Problem

A query gateway calls downstream services internally. Requests can reach those services without passing through the external route that checks permissions.

## Example

A caller allowed to enter the gateway asks for an object in another tenant. The downstream path must carry enough identity context to decide access to that object.

## Recorded approach

Place entitlement checks in a sidecar and resolve group membership near the boundary. The discussion raised latency, cache misses and policy skew.

### Hypothetical: GraphQL Authorization Gap

- GraphQL service-to-service RPCs bypass Envoy ingress, skipping per-route entitlement checks and rate limiting
- The proposed approach used sidecar enforcement; the interviewer noted trade-offs: latency, memory, cache misses, policy skew
    - Mitigation: resolve token and groups once near the boundary to enrich request context

[Anonymized session notes](../../../sources/notes/designs/authorization-for-internal-queries.md)
