# Mergeable Metrics & Quantiles (Aggregate, Then Quantile)

Why you can sum counts across a fleet but must **never** average percentiles — and how a metrics system computes an accurate cross-node p99 anyway. This is the algebra of [[system-design-concepts/commutative-aggregation]] applied to observability: transmit something order-independent, defer the non-mergeable step to the very end.

## The one-sentence mental model

> **Quantiles are not decomposable — no function of per-host p99s recovers the global p99. Counts and bucket counts are. So ship a mergeable structure (bucket counts or a sketch), fold it across nodes, and compute the quantile *last*: "aggregate, then quantile," never "quantile, then aggregate."**

## It's a representation problem, not a transport problem

The pitfall is often blamed on push. It isn't. It's a property of *what you transmit*:

- **Push done wrong:** classic StatsD `timing` computes `p99` **per agent** → not mergeable.
- **Push done right:** DogStatsD *distributions* / OTel ship a **DDSketch** → mergeable.
- **Pull done wrong:** scrape a pre-computed per-host `p99` gauge → equally broken.
- **Pull done right:** Prometheus scrapes cumulative `_bucket{le=…}` counts → mergeable.

The association with push is historical convention (StatsD emitted percentiles; Prometheus's data model happens to force buckets), not causation.

## What merges and what doesn't

**Mergeable (commutative + associative):** counts, sums, min, max, and **bucket counts** (a histogram is a vector of counts; add them bucket-wise). Means are recoverable too if you carry `_sum` and `_count` separately — avoiding the "average of averages" fallacy.

**Not mergeable:** any quantile. `p99(A ∪ B)` is not a function of `p99(A)` and `p99(B)`. Averaging, maxing, or count-weighting them is all wrong.

## How a scraper builds an accurate cross-node histogram

The client library never exposes a percentile — it exposes cumulative bucket counters + `_sum` + `_count`. The pipeline:

1. **Scrape raw bucket counts** from each instance (each keeps its own histogram).
2. **Sum bucket counts across instances**, bucket by bucket (exact merge): `sum by (le) (rate(http_request_duration_seconds_bucket[5m]))`.
3. **Interpolate the quantile last** from the merged histogram: `histogram_quantile(0.99, …)`.

Worked p99 (fleet histogram over a window, `_count = 3,000,000`; cumulative buckets le=0.50→2,955,000, le=1.00→2,985,000):
```
rank = 0.99 × 3,000,000 = 2,970,000   → straddles bucket (0.50, 1.00]
fraction = (2,970,000 − 2,955,000)/(2,985,000 − 2,955,000) = 15,000/30,000 = 0.5
p99 ≈ 0.50 + 0.5 × (1.00 − 0.50) = 0.75s
```

## Where the accuracy actually lives

The **merge is exact**; the **interpolation is the error source**. `histogram_quantile` assumes a *uniform* distribution inside the straddling bucket, so a true p99 of 0.62s in a 0.50–1.00 bucket still reports 0.75s. Error is bounded by bucket width, and it is **independent of scrape frequency**. Fixes:

- **Finer buckets near your SLO** (classic fixed-bucket histograms; must pre-choose boundaries).
- **Prometheus native histograms** — sparse, auto-scaled exponential buckets, bounded relative error, no pre-guessing. Current best practice.
- **Mergeable sketches** when you can't pre-decide buckets: **DDSketch** (relative-error guarantee `|q̂ − q| ≤ α·q`, exact merge), **t-digest** (great tails, approximate merge), **HDR histogram** (fixed relative precision, exact merge). Each node keeps a sketch; merge sketches, then read the quantile.

## Key points

- **"Aggregate, then quantile."** The single rule that keeps cross-node percentiles correct. It is the observability face of [[system-design-concepts/commutative-aggregation]]: bucket counts are a monotonic/associative aggregate, the quantile read is the non-mergeable step you defer.
- **Means need `_sum` + `_count`, not `avg(avg)`** — same principle: transmit the mergeable primitives, compute the ratio last.
- **Sketches degrade gracefully:** if one of 10 agents dies for a window, the merged p99 is built from the surviving 9 — a slight bias, not a blackout. Per-host-percentile designs lose that host-window entirely.
- Pull histograms also inherit **counter-reset recovery** for free, because each bucket is a cumulative counter (see [[system-design-concepts/counter-reset-and-restart-recovery]]).

## Interview angle

> "The mistake is averaging p99s across hosts — a percentile isn't decomposable, so no function of per-host quantiles gives you the global one. This is a representation problem, not push-vs-pull: whatever the transport, I ship a *mergeable* structure — cumulative bucket counts or a DDSketch — sum them across nodes, and compute the quantile last. With fixed buckets the only error is the uniform-interpolation assumption inside the straddling bucket, bounded by bucket width; native histograms or a relative-error sketch tighten that. Same discipline for the mean: carry `_sum` and `_count`, never average of averages."

## Connections
- [[system-design-concepts/commutative-aggregation]] — the general algebra: order-free aggregates fold freely; defer the non-mergeable step. Percentiles are the canonical *non*-mergeable operation
- [[system-design-concepts/red-metrics-exposition]] — the histogram exposition and full p99 worked math this page abstracts
- [[system-design-concepts/metrics-pull-vs-push]] — why this bites both transports, and where each ships buckets vs sketches
- [[system-design-concepts/counter-reset-and-restart-recovery]] — bucket-counter merge inherits reset correction; per-window percentiles do not
- [[theory/bloom-filters]] — another bounded-error probabilistic structure traded against exactness

## Sources
- [[sources/docs/metrics-collection-observability-self-study]] — §2 mergeability, §3 histogram p99 worked example
</content>
