# Rate Limiting

A rate limiter answers one question — *"has identity X exceeded N events per time-unit T?"* — and the entire design space is about **where you keep the counter** (in-process vs shared store), **how you shape the window** (see [[theory/rate-limiting-algorithms]]), and **what you trade** when the counter is wrong (accuracy vs latency vs availability).

## Why rate limit — requirements drive the trade-offs

| Goal | Protects against | Design implication |
|---|---|---|
| Overload / stability | Thundering herds, retry storms, spikes | Tiny latency budget; degrade gracefully |
| Fairness | One noisy tenant starving others | Per-identity keys; global coordination matters |
| Cost control | Metered downstreams (LLM tokens, 3rd-party APIs) | Quota semantics; accuracy > latency |
| Abuse / security | Credential stuffing, scraping, L7 DoS | Keys on IP/fingerprint; often paired with WAF |
| Business tiering | Free vs paid quotas | Config-driven, hot-reloadable |

### It's one tool in a load-management family
Rate limiting is **proactive and static** — it enforces a fixed contract keyed on *identity* regardless of live system health, and governs only the *rate of arrivals* (nothing about a request that turns slow once admitted). Place it among its neighbors: **admission control** (the umbrella — rate limiting is one kind), **concurrency limiting** (caps in-flight requests, self-throttles when downstream slows), **circuit breaking** (stops traffic to a failing dependency), **load shedding** (drops least-important work under overload), and **backpressure** (signal "I'm full" up the call chain). A strong answer combines them — e.g. a per-tenant rate limit for fairness *plus* adaptive concurrency to survive a downstream that gets slow in a way the rate limit can't see.

## The two axes: Local vs Global, L4 vs L7

The single most useful framing. Every deployment is a point on a 2×2 grid.

- **Local** — counter in-process, per proxy instance. Zero network hop, but with M proxies the effective limit is `M × configured` unless you divide (which breaks on autoscale).
- **Global** — counter in a shared store (Redis) fronted by a service. One authoritative count across the fleet, at the cost of an RPC per decision.
- **L4 (transport)** — sees connections, IP:port, SNI. No HTTP semantics.
- **L7 (application)** — sees the full request: path, method, headers, JWT/mTLS identity. Enables per-API, per-user policy.

| Dimension | Local (in-proc token bucket) | Global (RLS + Redis) |
|---|---|---|
| Latency added | ~sub-µs, no I/O | 1 network RTT; ~ms |
| Accuracy across fleet | Limit × N replicas | Authoritative, fleet-wide |
| Blast radius | None — self-contained | Redis + service in request path |
| Burst model | Token bucket (controlled) | Fixed window (2× boundary burst) |
| Policy change | Push proxy config | Hot-reload service config independently |

**The pattern that wins interviews: layer them.** A cheap local limiter as tier 1 (protects even if the global store is down; sheds obvious floods before they cost a round-trip) plus a global limiter as tier 2 for accurate fleet-wide fairness and quotas. [[tech/envoy-ratelimit-service|Envoy]] is explicitly built for this — local and global descriptors coexist on the same route.

## Distributed correctness — the hard parts

- **Atomicity / race conditions** — naive `GET; if ok INCR` has a read-modify-write race (two proxies both read 99, both allow → 101). Fix: use **`INCR`'s atomic return value** as the decision. #1 correctness bug in home-grown limiters. Redis's per-shard atomic `INCR` (see [[tech/aws-elasticache-redis]]) is exactly why it's the canonical backing store.
- **Clock skew** — fixed-window buckets come from each node's local clock; mitigate with NTP + units coarse enough that tens-of-ms skew is negligible.
- **Hot keys** — one giant tenant concentrates on one Redis slot; you can't shard a single counter. Mitigate with a local over-limit cache, or split a logical limit into K sub-counters summed periodically.
- **Scaling past one store** — cut round-trips (batch, local cache), shard across a Redis Cluster (counters are independent → clean), then push to **per-node local budgets with async reconciliation** (each node enforces a share of the global budget, reconciles via a coordinator or gossip). Trades exactness for removing the per-request hop — name the overshoot cost.

## Identity is composed, not intrinsic
"What is the caller's identity — IP or service?" is a false choice. A limiter keys on **whatever attributes you extract into the descriptor**. At the **edge**, identity is a spoofable network/header signal you must harden (trusted-hop config, upstream auth); **inside a mesh**, it's a verified cryptographic service principal (mTLS/SPIFFE) you should prefer over IP. Detail in [[tech/envoy-ratelimit-service]].

## Key points
- The design space = counter location × window shape × failure trade-off.
- Local = fast + independent but inaccurate across a fleet; global = accurate but adds an RTT + a dependency. Layer both.
- Use `INCR`'s return value to avoid the read-modify-write race — never `GET`-then-`INCR`.
- Fail-open for *stability* limiters, fail-closed for *abuse/cost* limiters — decide per limit.
- Rate limiting is proactive/static; pair it with reactive tools (concurrency, circuit breaking, shedding).

## Interview angle

> "A rate limiter answers 'has identity X exceeded N per T?', so the design is three choices: where the counter lives, how the window is shaped, and what happens when it's wrong. Local in-process is sub-microsecond but each replica counts separately; global in Redis is authoritative but adds a round-trip and a dependency — so I layer a local token-bucket tier for cheap always-on protection under a global fixed-window tier for fleet-wide fairness. The correctness cornerstone is using INCR's atomic return as the decision, not GET-then-INCR. And I place the limiter in its family — it's proactive and static, so I pair it with adaptive concurrency and circuit breaking for the reactive failures it can't see."

## Connections
- [[theory/rate-limiting-algorithms]] — the five window shapes (fixed/sliding/token/leaky) and their trade-offs
- [[tech/envoy-ratelimit-service]] — the concrete global RLS: gRPC + descriptors + Redis, and the identity model
- [[tech/istio-service-mesh]] — how the limiter is wired onto Envoy at the edge in a mesh deployment
- [[tech/aws-elasticache-redis]] — the atomic `INCR` per shard is what makes Redis the canonical counter store
- [[system-design-concepts/work-distribution]] — both are about coordinating a fleet without a central bottleneck

## Sources
- [[sources/docs/rate-limiting-study-guide]] — §1 why + load-management family, §3 the 2×2, §6–8 trade-offs/correctness/scaling
- [rate-limiting-study-guide.html](https://github.com/redblackcoder/interview-prep-raw/blob/main/docs/rate-limiting-study-guide.html) — full staff+ guide grounded in envoyproxy/ratelimit
