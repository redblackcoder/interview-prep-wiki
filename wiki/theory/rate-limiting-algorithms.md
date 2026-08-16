# Rate-Limiting Algorithms

Every limiter is one of five algorithms (or a hybrid). Know the counter each keeps, its memory cost, its burst behavior, and its failure edge — this is a common interview filter. See [[system-design-concepts/rate-limiting]] for where they're deployed.

## The five

**1. Fixed window counter** — divide time into fixed buckets (each wall-clock minute); keep one integer per (key, bucket); `INCR` on each request, reject over limit; the bucket resets when the clock rolls over.
- Memory: **O(1)** per active key — cheapest. Reduces to a single atomic `INCR` + `EXPIRE`, which any shared store supports natively.
- Flaw: the **2× boundary burst** — with a limit of 100/min, a client sends 100 at 00:00:59 and 100 at 00:01:00 → 200 in ~1 second, all "legal."

**2. Sliding window log** — store a timestamp for *every* request in a sorted set; drop entries older than `now − W` and count what remains.
- Perfectly accurate, no boundary burst. Memory: **O(N)** per key — expensive and unbounded under attack (the log itself becomes a DoS vector). Use for low-volume, high-accuracy (e.g. "5 password resets/hour").

**3. Sliding window counter (approximation)** — keep current + previous fixed-window counters, weight the previous by how far you are into the current window: `est = curr + prev * (1 − elapsed/W)`.
- Memory: **O(1)** (two counters). Smooths the boundary burst to a bounded few-% over/undershoot. The pragmatic middle ground (Cloudflare's edge approach).

**4. Token bucket** — a bucket holds up to `B` tokens, refills at `r`/sec; each request removes one; empty ⇒ reject. State is `(tokens, last_refill_ts)`, refilled lazily.
- **Allows controlled bursts** up to `B` while enforcing long-run average `r` — what humans usually *mean* by "rate limit." Memory: O(1). Used by Envoy's **local** limiters.

**5. Leaky bucket** — requests enter a FIFO queue draining at a fixed rate; overflow ⇒ reject. Output is perfectly smooth (traffic shaping). **Token bucket permits bursts; leaky bucket forbids them** — distinguishing these crisply is the classic filter.

## Comparison

| Algorithm | State / key | Bursts? | Accuracy | Typical home |
|---|---|---|---|---|
| Fixed window | 1 int | 2× at boundary | Low | Global RLS ([[tech/envoy-ratelimit-service]]) |
| Sliding log | O(N) timestamps | None | Exact | Low-volume, security |
| Sliding counter | 2 ints | Smoothed | ~99% | Edge / CDN |
| Token bucket | 2 values | Up to B (controlled) | Good | Envoy local L4/L7 |
| Leaky bucket | queue + rate | None (shapes) | Good | Traffic shaping / QoS |

## Why a global service picks fixed window
The [[tech/envoy-ratelimit-service|envoyproxy/ratelimit]] service uses **fixed window** deliberately: it collapses to a single atomic `INCRBY` + `EXPIRE`, O(1) memory, natively supported by Redis/Memcached, and correct under concurrency without locks. Sliding-log needs per-request O(N) storage + trimming; sliding-counter needs two keys + math. At the RLS's throughput, atomic-counter simplicity wins, and the boundary-burst weakness is mitigated by short units, TTL jitter, and a local over-limit cache. The window itself is **encoded in the cache key** via time flooring (`(now / unit) * unit`), so "which window am I in" needs no stored state and old windows self-expire.

## Key points
- Fixed window: O(1), atomic, but 2× boundary burst — the global-service default for exactly that simplicity.
- Sliding log: exact but O(N) and a DoS vector; sliding counter: O(1) approximation that smooths the burst.
- Token bucket allows bursts up to B (average r); leaky bucket smooths output completely — the one-line distinction.
- The window can live in the *key* (time flooring) instead of stored state, so TTL garbage-collects old buckets.

## Interview angle

> "Five shapes. Fixed window is one integer per bucket — O(1), reduces to an atomic INCR, but allows a 2× burst at the boundary. Sliding log stores every timestamp: exact but O(N) and itself a DoS vector. Sliding counter weights the previous window — O(1) and smooths the burst to a few percent. Token bucket allows controlled bursts up to the bucket size while capping the average; leaky bucket forbids bursts and smooths output — that's the distinction people miss. A global service picks fixed window because it's the only one that's a single atomic op the shared store supports natively; you mitigate the burst with short units and jitter."

## Connections
- [[system-design-concepts/rate-limiting]] — where each algorithm lives (local token bucket vs global fixed window) and the distributed-correctness context
- [[tech/envoy-ratelimit-service]] — the fixed-window-over-atomic-counter implementation, key construction, local over-limit cache
- [[tech/aws-elasticache-redis]] — atomic `INCR`/`INCRBY` per shard is what makes fixed-window correct under concurrency

## Sources
- [[sources/docs/rate-limiting-study-guide]] — §2 the five algorithms, §5.3–5.4 fixed-window + cache-key construction
- [rate-limiting-study-guide.html](https://github.com/redblackcoder/interview-prep-wiki/blob/master/sources/docs/rate-limiting-study-guide.html) — algorithm diagrams and comparison table
