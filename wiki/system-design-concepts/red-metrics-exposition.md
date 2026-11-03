# RED Metrics Exposition (Why a 60s Scrape Is Accurate at 50k QPS)

How Requests / Errors / Duration are actually exposed and collected — and the one idea that makes infrequent scraping accurate for a high-QPS service. Worked with real numbers for a service doing **50,000 QPS across 10 instances (5,000 QPS each)**.

## The one-sentence mental model

> **Aggregation happens at event time, in memory, on every single request. The scrape (or flush) only samples the running totals. So scrape/flush frequency bounds your *time resolution* — never the *correctness of your counts*.**

## Pull (Prometheus), scraped every 60s

### How RED is exposed
- **Requests** → a cumulative counter `http_requests_total`.
- **Errors** → a *labeled subset* of the same counter, `http_requests_total{status="500"}`.
- **Duration** → a histogram = cumulative `_bucket{le=…}` counters + `_sum` + `_count`.

Every request does, in-process (atomic add, ~ns): `requests_total++`; add latency to `_sum`; `_count++`; increment **every** bucket with `le ≥ latency` (that's "cumulative"). The 60s scrape just *reads* these totals — no event is dropped no matter the QPS, because counting already happened at event time.

### Requests & Errors — the math
```
scrape N   (t=0):  http_requests_total = 12,000,000
scrape N+1 (t=60): http_requests_total = 12,300,000
rate = (12,300,000 − 12,000,000)/60 = 5,000 req/s     ← exact window average
```
`rate()` also auto-corrects counter resets (see [[system-design-concepts/counter-reset-and-restart-recovery]]). Fleet-wide:
```promql
sum(rate(http_requests_total[5m]))                                   # 50,000 req/s
sum(rate(http_requests_total{status=~"5.."}[5m]))
  / sum(rate(http_requests_total[5m]))                               # error ratio
```
Counts are **exact**. The only cost of 60s: temporal resolution — a 5s spike is smeared across the window (worse with `rate[5m]`). That's a *resolution* price, not a *correctness* price.

### Duration — histogram math to p99
Fleet cumulative buckets over the 60s window (`_count = 3,000,000`):

| latency range | count | cumulative `_bucket{le}` |
|---|---|---|
| ≤0.05 | 1,500,000 | le=0.05 → 1,500,000 |
| (0.05,0.10] | 900,000 | le=0.10 → 2,400,000 |
| (0.10,0.25] | 450,000 | le=0.25 → 2,850,000 |
| (0.25,0.50] | 105,000 | le=0.50 → 2,955,000 |
| (0.50,1.00] | 30,000 | le=1.00 → 2,985,000 |
| (1.00,∞) | 15,000 | le=+Inf → 3,000,000 |

```
rank = 0.99 × 3,000,000 = 2,970,000   → straddles (0.50, 1.00]
fraction = (2,970,000 − 2,955,000)/(2,985,000 − 2,955,000) = 0.5
p99 ≈ 0.50 + 0.5 × (1.00 − 0.50) = 0.75s
mean = _sum / _count
```
```promql
histogram_quantile(0.99, sum by (le) (rate(http_request_duration_seconds_bucket[5m])))
```
The merge (summing buckets across instances) is exact; the interpolation is the only error (see [[system-design-concepts/mergeable-metrics-and-quantiles]]). **Why 60s is fine:** the histogram is 300k in-memory increments/instance/window; the scrape reads accumulated counts. Zero events sampled away.

## Push (UDP → sidecar → aggregate → push)

Aggregation moves *out of the app* into a local **StatsD/DogStatsD sidecar**. The app fires one UDP datagram per event (fire-and-forget, no backpressure); the sidecar aggregates over a **flush interval** (StatsD default 10s), then pushes.

Wire protocol:
```
requests:1|c              counter +1
requests:1|c|@0.1         counter +1, SAMPLED at 10% (agent multiplies by 10)
request.duration:73|ms    timer, this request took 73 ms
request.duration:73|d     DogStatsD distribution (ships a mergeable sketch)
```
UDP overflow → **silent packet drop** (the lossy tradeoff). High-QPS services sample to cut volume.

### Counter with sampling — unbiased but noisy
Over 10s at 5,000 QPS = 50,000 requests, `@0.1`:
```
packets ≈ 5,000 → corrected count = 5,000/0.1 = 50,000    ✓
X ~ Binomial(50000, 0.1): E=5,000, Var=Np(1−p)=4,500, sd≈67
estimate = 10X → sd ≈ 670 → relative error ≈ 670/50,000 ≈ 1.3%
```
Sampling trades exactness for fewer packets; **pull would be exact.**

### Where the percentile "values" come from
The app submits **each request's duration individually**; it computes nothing. The sidecar **accumulates every latency received during the flush window** — that buffer *is* the sample list. To produce a percentile you need the distribution, so the sidecar keeps either:
- the **full list** (naive StatsD): 5,000 QPS × 10s = **500,000 values/flush** — memory-heavy, and yields a *per-host* percentile that can't merge; or
- a **bounded summary** (histogram / DDSketch): O(1) per event, and mergeable across agents.

Naive nearest-rank on `[12,15,18,20,22,25,30,45,60,95]` (N=10, position `⌈p·N⌉`): `p90 = 9th = 60ms`, `p99 = 10th = 95ms`; sidecar flushes `count=10, mean=34.2, upper_90=60, upper_99=95, sum=342, max=95`. Correct at fleet scale only if it ships buckets/sketches, not `upper_99`.

## Side-by-side

| | Pull (Prometheus, 60s) | Push (StatsD/UDP, 10s flush) |
|---|---|---|
| Aggregation | in-process library, event time | sidecar, event time |
| Wire payload | cumulative totals (scraped) | per-flush aggregates/sketches (pushed) |
| Counts | **exact** | exact if unsampled; **±√(N(1−p)/p)** if sampled |
| Duration | exact buckets; error = interpolation | correct iff buckets/sketches; wrong if per-host percentiles |
| Under load | scrape slows (safe) | UDP drops silently |
| Interval cost | time resolution only | time resolution + sampling variance |

## Key points
- **Both models count every event** — aggregation is at event time; the network step only ships pre-aggregated totals. That is *why* an infrequent scrape/flush is accurate at high QPS.
- Accuracy is governed by (a) **mergeable representation** for duration (buckets/sketches, never per-host percentiles) and (b) **exact vs sampled** counting.
- Scrape/flush frequency sets **temporal resolution**, full stop.

## Interview angle
> "A 60s scrape is accurate at 50k QPS because the client library increments in-memory counters on every request — the histogram is 300k increments per window — and the scrape just reads the running totals. Nothing is sampled away; 60s only limits how finely I can slice time. For duration I expose cumulative bucket counters, sum them across instances, and interpolate p99 at query time. Push is symmetric: the sidecar aggregates per-event and flushes every 10s — but if I sample I inject ~1% variance, and I must ship a mergeable sketch, not a per-host percentile."

## Connections
- [[system-design-concepts/mergeable-metrics-and-quantiles]] — why the duration histogram is exposed as buckets and how p99 is computed correctly across nodes
- [[system-design-concepts/counter-reset-and-restart-recovery]] — what happens to these counters/histograms across process and agent restarts
- [[system-design-concepts/metrics-pull-vs-push]] — the transport decision this exposition sits under
- [[system-design-concepts/event-time-vs-processing-time]] — "temporal resolution" and windowing are the same event-vs-observation distinction
- [[theory/latency-numbers]] — the ~ns atomic increment vs ~µs–ms network hop is why aggregation stays in-process/local before crossing the wire

## Sources
- [[sources/docs/metrics-collection-observability-self-study]] — §3 RED exposition, scrape math, StatsD/UDP sidecar, sampling variance
</content>
