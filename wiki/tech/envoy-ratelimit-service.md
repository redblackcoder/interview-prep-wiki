# Envoy Global Rate Limit Service (RLS)

`envoyproxy/ratelimit` is a stateless **Go/gRPC service** that reads a rate-limit config, receives descriptor-based requests from Envoy, composes cache keys, and atomically increments counters in Redis. It's the reference implementation of *global* [[system-design-concepts/rate-limiting|rate limiting]] and a rich source of staff+ design detail. Wired into a mesh via [[tech/istio-service-mesh|Istio]].

## The request path
1. Envoy's `http.ratelimit` filter builds **descriptors** (ordered key/value tuples from request attributes) and calls `ShouldRateLimit(domain, [descriptors])` over gRPC.
2. The service snapshots config under an `RWMutex` (so a hot-reload can't tear the read) and walks the **descriptor tree** to find the most specific matching limit.
3. A local over-limit cache short-circuits known-blocked keys; otherwise it pipelines `INCRBY` + `EXPIRE` to Redis.
4. **Overall code is OVER_LIMIT iff any (non-quota) descriptor is over** — a logical AND of "everything must be OK." Envoy returns **429** locally on OVER_LIMIT; the upstream is never contacted.

## Descriptors and the config tree
A **descriptor** describes one request dimension, e.g. `[(key=api_key, value=X), (key=path, value=/export)]`. Config is a **tree**; `GetLimit()` is a nested-map traversal (try `key_value`, then `key`, then pre-split wildcard patterns), O(depth) ≈ 1–3 lookups. This is how "per-API" policy works in the *global* path: a `(path, /export)` nested under `(api_key, X)` expresses "10k/day per key, but 100/min on the export path." (In the *local* path, per-API comes from route matching instead.)

## Identity is composed at config time — the key insight
**Envoy has no built-in notion of "client identity."** `GetLimit()` never inspects an IP or a certificate — it matches descriptors against config. Identity is *composed* by Envoy from request/connection attributes via **rate-limit actions** (descriptor generators) on the route or network filter. **Whatever attributes you extract becomes the identity.**

| Action | Value emitted | Trust / stability |
|---|---|---|
| `remote_address` / `masked_remote_address` | client IP / subnet | Spoofable at edge; IPs ephemeral |
| `request_headers` | header value (`x-api-key`, `:path`) | Only as trustworthy as the header |
| `source_cluster` | the *local* Envoy's own cluster | Stable but direction-sensitive |
| `metadata` | mTLS peer principal / verified JWT claim | **Strongest** — verified, not spoofable |
| `generic_key` | a static string on the route | Fixed |

### Edge vs mesh — the crux
The trustworthy signals differ completely by direction:
- **External / ingress** — clients anonymous; no mTLS peer identity. Key on client IP (`masked_remote_address`), an API key/token from a header (ideally a `metadata` read of a *verified* claim after JWT/`ext_authz`), or a `generic_key` per route.
- **Internal mesh** — the "client" is another workload authenticated by mTLS, carrying a verified **SPIFFE identity** (`spiffe://cluster.local/ns/payments/sa/checkout`). Key on that principal via `metadata` — cryptographically verified, not spoofable. **Pod IP is a poor key east-west** (pods churn/autoscale).

**The X-Forwarded-For trap** (#1 edge mistake): behind a cloud L4 LB, `remote_address` returns the *LB's* IP unless you configure trusted-hop handling (`xff_num_trusted_hops`/Proxy Protocol). Get it wrong one way → every client shares one bucket (the whole API collapses to one counter); the other way (blindly trusting client-supplied XFF) → an attacker rotates fake XFF values to mint infinite identities. **IP-as-identity is only as good as your trusted-hop config.** The compressed rule: *at the edge, identity is a spoofable signal you must harden; inside the mesh, it's a verified principal you should prefer over IP* — same RLS, same descriptors, only the source/trust of descriptor *values* changes. The RLS being identity-agnostic is the feature: one service correctly limits a public API keyed on API-key and internal traffic keyed on SPIFFE, with no code change.

## Design touches worth citing
- **Fixed-window over an atomic counter** — `INCRBY`'s return value *is* the decision, so concurrent Envoys are serialized correctly by Redis with no read-modify-write race (see [[theory/rate-limiting-algorithms]]).
- **Cache key encodes the window** — `prefix + domain + values + floored-window-start`; when the clock rolls into the next bucket the key changes, the counter starts fresh, and TTL cleans the old key. No sweeper.
- **Local over-limit cache (freecache)** — caches *only over-limit verdicts* for the rest of the window. Safe by construction: caching "OK" would let each proxy skip the shared `INCR` and under-count; caching "OVER" only rejects what Redis already rejected, turning a hot blocked key from N round-trips into N local hits. **Asymmetric caching is a strong senior signal.**
- **Redis data path** — pipelines all descriptors into one round-trip; TTL **expiry jitter** avoids a synchronized cache-cliff; optional separate Redis for per-second limits; cluster/sentinel topologies; keys shard by hash so each counter stays on one shard (preserving atomicity).
- **Fail-open vs fail-closed** — on Redis/RLS failure Envoy's `failure_mode_deny` decides: default **fail-open** (a limiter shouldn't take down the service) for stability limiters, **fail-closed** for abuse/cost limiters (else knocking over Redis disables the limiter). Choose per limit; a local backstop provides a floor.
- **Shadow mode** — run the full pipeline but always return OK; watch the "would-have-blocked" stat before enforcing. **`near_limit`** (80%) is the alert metric.

## Key points
- Stateless service → scale horizontally; all state (counters) is externalized to Redis.
- Descriptors *are* the identity — the service is deliberately identity-agnostic.
- Edge identity is spoofable and must be hardened; mesh identity is a verified SPIFFE principal.
- Cache only over-limit verdicts (safe); never cache OK (breaks global accuracy).
- Fail-open for stability, fail-closed for abuse — a per-limit decision.

## Interview angle

> "The Envoy RLS is a stateless Go/gRPC service over Redis. Envoy builds descriptors from request attributes and calls ShouldRateLimit; the service walks a descriptor tree for the most specific limit and does an atomic INCRBY+EXPIRE — the increment's return value is the decision, which is how it avoids the read-modify-write race. The elegant bits: the window is encoded in the cache key by time-flooring so old buckets just expire, and it caches only over-limit verdicts locally because caching OK would break global accuracy. The deep point is identity: Envoy has no built-in 'client' — whatever attribute you extract into the descriptor is the identity, so at the edge you harden a spoofable IP/header and inside the mesh you key on the verified SPIFFE principal, same service either way."

## Connections
- [[system-design-concepts/rate-limiting]] — the global-limiter half of the local-vs-global framing this implements
- [[theory/rate-limiting-algorithms]] — why fixed-window-over-atomic-counter, and the cache-key window encoding
- [[system-design-concepts/client-identification]] — deepens the descriptor identity model: authenticated credential vs anonymous IP/ASN/JA4 at the edge
- [[system-design-concepts/global-rate-limiting]] — running this single-region counter across regions without a synchronous global store
- [[tech/istio-service-mesh]] — how the RLS filter + cluster are patched onto the ingress gateway
- [[tech/aws-elasticache-redis]] — the Redis atomic `INCR`/pipeline/cluster behavior the RLS depends on
- [[system-design-concepts/network-security-layers]] — the edge-vs-mesh trust distinction mirrors payload-vs-envelope; mTLS/SPIFFE is the verified-identity anchor

## Sources
- [[sources/docs/rate-limiting-study-guide]] — §5 the global RLS (architecture, gRPC contract, descriptor tree, identity, algorithm, cache, failure modes)
- [rate-limiting-study-guide.html](https://github.com/redblackcoder/interview-prep-wiki/blob/master/sources/docs/rate-limiting-study-guide.html) — repo-grounded walkthrough of envoyproxy/ratelimit internals
- [[sources/docs/istio-rls-deployment-walkthrough]] — the runnable manifests that wire this service to a gateway
