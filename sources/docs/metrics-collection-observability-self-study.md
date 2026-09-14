---
source: Metrics collection & observability — self-study Q&A session
source_url: —
author: self (Claude-assisted study session)
type: doc
date_extracted: 2026-09-08
topic: system-design-concepts / tech
---

# Metrics Collection & Observability — Self-Study Q&A

> A staff-level study session working through how metrics pipelines actually
> collect Requests/Errors/Duration (RED) at scale. Started from a Stripe
> "design a metrics service" interview prompt, then drilled into pull vs push,
> why percentiles can't be averaged, how a 60s scrape stays accurate at
> 50k QPS, and what happens to counters and percentiles across process restarts.
> All numbers below are worked by hand.

## 1. Pull vs push — the transport decision

**Pull (Prometheus model):** service exposes `/metrics` in OpenMetrics text; a
scraper holds the target list (via service discovery) and scrapes each target on
an interval. The monitor initiates contact; the service is passive.

**Push (StatsD / OTLP model):** the SDK / sidecar / daemon sends metrics outward
to a collector / queue / ingestion endpoint; a consumer writes to the TSDB. The
producer initiates contact.

Key insight: **pull inverts control** — the monitoring system owns the target
list, so a failed scrape is itself the `up=0` signal. Push has no inherent
liveness signal (down vs idle look identical).

Pull pros: free liveness, natural backpressure, no creds on producer, curl-able,
multi-consumer, scraper stamps trustworthy target labels.
Pull cons: mandatory service discovery, batch/ephemeral jobs don't fit
(Pushgateway is the patch), inbound reachability required (NAT/firewall/SaaS
pain), resolution capped by scrape interval.

Push pros: handles ephemeral/event-driven (Lambda, batch, browser), crosses NAT
outbound, higher event-level resolution, producer-controlled emission +
buffering, discovery-free.
Push cons: down-vs-idle ambiguity, backpressure/thundering-herd risk (UDP drops
silently), creds on producer, harder local debug, aggregation-correctness
pitfalls.

Real architectures: Prometheus/K8s = pull (K8s API is the registry) →
`remote_write` **push** into Cortex/Mimir/Thanos/VictoriaMetrics (hybrid);
StatsD/DogStatsD (Etsy/Datadog), Facebook Gorilla/ODS, Uber M3, Netflix Atlas,
CloudWatch = push; OTel Collector bridges both (scrape one side, push the other).

Rule of thumb: **pull at the instrumented edge where discovery + reachability
are solid; push across trust/network boundaries and for ephemeral work;
Collector/agent as the seam; push into the long-term store.**

## 2. Why percentiles can't be merged (representation, not transport)

Quantiles are **not decomposable**: `p99` of host A and `p99` of host B cannot
be combined into the global `p99` by any function (avg/max/weighted are all
wrong). This applies to BOTH pull and push — it is a property of the
*representation*, not the transport. Counts, sums, and **bucket counts** ARE
mergeable (bucket-wise addition is associative + commutative).

Fix in both models: **transmit a mergeable structure and compute the quantile
last** — "aggregate, then quantile," never "quantile, then aggregate."

- Prometheus (pull-done-right): client exposes cumulative `_bucket{le=…}`
  counters + `_sum` + `_count`. PromQL sums buckets across instances, then
  `histogram_quantile` interpolates. Correct global p99.
- Datadog distributions / OTel (push-done-right): agent builds a **DDSketch**
  (relative-error guarantee, exact merge) and ships the sketch; backend merges
  then reads the quantile.
- Anti-pattern: classic StatsD `timing` computes p99 **per host** → not mergeable.

Interpolation caveat: reading a quantile out of fixed buckets assumes a uniform
distribution inside the straddling bucket → error bounded by bucket width. Fix
= finer buckets near the SLO, or **Prometheus native histograms** (auto-scaled
exponential buckets, bounded relative error, no pre-guessing), or a sketch
(DDSketch/t-digest/HDR).

## 3. How RED is exposed & why a 60s scrape is accurate at 50k QPS

Core principle: **aggregation happens at event time, in memory, on every
request. The scrape/flush only samples the running totals.** Scrape frequency
bounds *time resolution*, not *count correctness*.

Setup: 50,000 QPS across 10 instances (5,000 QPS each).

**Requests (counter), Errors (labeled subset of the counter):**
```
scrape N   (t=0):  http_requests_total = 12,000,000
scrape N+1 (t=60): http_requests_total = 12,300,000
rate = (12,300,000 − 12,000,000)/60 = 5,000 req/s   ← exact window average
```
Fleet: `sum(rate(http_requests_total[5m]))` = 50,000 req/s.
Error ratio: `sum(rate(http_requests_total{status=~"5.."}[5m])) / sum(rate(http_requests_total[5m]))`.

**Duration (histogram):** each request increments every cumulative bucket with
`le ≥ latency`, plus `_sum` and `_count`. Worked fleet example over 60s
(3,000,000 requests):

| latency range | count | cumulative `_bucket{le}` |
|---|---|---|
| ≤0.05 | 1,500,000 | le=0.05 → 1,500,000 |
| (0.05,0.10] | 900,000 | le=0.10 → 2,400,000 |
| (0.10,0.25] | 450,000 | le=0.25 → 2,850,000 |
| (0.25,0.50] | 105,000 | le=0.50 → 2,955,000 |
| (0.50,1.00] | 30,000 | le=1.00 → 2,985,000 |
| (1.00,∞) | 15,000 | le=+Inf → 3,000,000 |

p99 by hand: rank = 0.99×3,000,000 = 2,970,000 → straddles (0.50,1.00].
`fraction = (2,970,000 − 2,955,000)/(2,985,000 − 2,955,000) = 15,000/30,000 = 0.5`
`p99 ≈ 0.50 + 0.5×(1.00 − 0.50) = 0.75s`. PromQL:
`histogram_quantile(0.99, sum by (le) (rate(http_request_duration_seconds_bucket[5m])))`.
mean = `_sum/_count`.

60s is fine because the histogram is 300k in-memory increments/instance/window;
the scrape reads accumulated counts — zero events dropped. 60s only smears
sub-window time detail.

**Push (StatsD/UDP sidecar):** app fires one UDP datagram per event
(`request.duration:73|ms`), fire-and-forget; sidecar accumulates over a flush
interval (default 10s) and pushes. UDP overflow → silent packet drop.

Counter with sampling `requests:1|c|@0.1`: over 10s at 5,000 QPS = 50,000
requests → ~5,000 packets → corrected count = 5,000/0.1 = 50,000. Sampling adds
variance: X~Binomial(50000, 0.1), E=5000, Var=4500, sd≈67 → estimate 10X has
sd≈670 → relative error ≈1.3% (pull would be exact).

Where the percentile "values" come from: the app submits **each request's
duration** individually; the sidecar **accumulates all durations in the flush
window** — that buffer IS the sample list. To compute a percentile you need the
distribution, so the sidecar keeps either the full list (naive, memory-heavy:
50k QPS × 10s = 500k values/flush) or a bounded summary (histogram / DDSketch,
O(1) per event). Naive nearest-rank on `[12,15,18,20,22,25,30,45,60,95]`:
`p90 = ⌈0.9×10⌉ = 9th = 60ms`, `p99 = ⌈0.99×10⌉ = 10th = 95ms`.

## 4. Restarts — counter reset handling and the percentile asymmetry

**Pull counter reset (`rate`/`increase`):** counters reset to 0 on restart.
Correction scans adjacent samples; on a *decrease* it adds the pre-drop value:
```
correction=0; prev=first
for v in later: if v<prev: correction += prev; prev=v
increase = (last − first) + correction
```
Worked example `100, 1200, 200, 300` over 30s:
drop 1200→200 detected → correction=1200 → increase = (300−100)+1200 = 1400 →
rate ≈ 46.7/s.
**Blind spot:** the rule only fires on a *decrease*. `200→300` went up, so a
restart that climbed back above the previous sample within one interval is
**undetected** → silent undercount. Argues for shorter scrape intervals on
high-QPS services.

**Push counter:** classic StatsD ships per-flush **deltas** (not cumulative), so
nothing goes negative; a restart = a short/missing flush = a **silent dip/gap**
(worse than pull — no reset signal). Cumulative-counter agents reset to 0 on
restart → downstream applies the same correction + same blind spot.

**Percentile asymmetry:** per-window percentiles/sketches are NOT cumulative —
the buffer empties each flush, so there's nothing to reset-correct. A lost
window's distribution is **unrecoverable**, and the loss is **adversarial**:
crashes correlate with slow/error requests, so the tail you care about is
exactly what's dropped → p99 looks artificially good when things were worst.
Graceful degradation comes from (a) mergeable sketches + fleet-merge (one dead
agent → 9/10 instances still contribute), and (b) the pull symmetry: a
Prometheus histogram is just cumulative bucket counters, so `rate(_bucket[5m])`
inherits the counter-reset correction **bucket by bucket** for free.

**The asymmetry to remember:** cumulative representations (pull counters, pull
histogram buckets) can be reset-corrected; per-window percentile pushes cannot.

## My Understanding
- The pull/push debate is usually mis-stated as a transport war. The load-bearing
  distinctions are actually: (1) liveness signal (pull free, push must synthesize),
  (2) reachability/discovery, (3) mergeable vs non-mergeable representation, and
  (4) exact counting vs sampled. Transport correlates with these but doesn't cause them.
- "Aggregation at event time" is the unlock for the 60s-scrape question — it's the
  same reason both models stay accurate: the network step ships pre-aggregated
  totals, it never samples the event stream.
- The percentile-mergeability rule is the same algebra as
  [[system-design-concepts/commutative-aggregation]]: bucket counts are a
  commutative/associative aggregate; percentiles are not — so you fold buckets and
  defer the non-mergeable step (the quantile read) to the very end.

## Open Questions
- Native histograms vs DDSketch in practice: when does the exponential-bucket
  Prometheus model beat shipping sketches, given both give bounded relative error?
- Exemplars / trace linking: how does attaching a trace ID to a histogram bucket
  change the exposition cost at 50k QPS?
