# Counter Resets & Restart Recovery

What happens to metrics when a process (or agent) restarts and its in-memory counters drop to zero — how `rate()` recovers, where it silently fails, and why **percentiles can't be recovered at all**.

## The one-sentence mental model

> **A cumulative counter can be reset-*corrected* — the monitor sees the drop, assumes it fell to zero, and adds the pre-drop value back. A per-window percentile cannot: it never accumulates across windows, so a lost window is gone, and the loss is biased toward the tail you most care about.**

## Pull: counter reset correction in `rate()` / `increase()`

Counters are monotonic in-process and reset to 0 on restart. Prometheus stores the raw samples; the correction lives in `rate()`/`increase()`, scanning adjacent samples:
```
correction = 0; prev = first_sample
for each later sample v:
    if v < prev:            # a DROP → reset happened
        correction += prev  # assume it fell from ~prev to 0
    prev = v
increase = (last − first) + correction
```
The assumption: at a reset, the peak ≈ the last value observed before the drop.

### Worked example — samples `100, 1200, 200, 300` over 30s
```
prev=100
1200 vs 100  → up,   no reset.                 prev=1200
 200 vs 1200 → DROP  → correction += 1200.     prev=200
 300 vs 200  → up,   no reset.                 prev=300
increase = (300 − 100) + 1200 = 1400   →   rate ≈ 1400/30 ≈ 46.7 req/s
```
The restart between t=10 and t=20 is handled: the 1200 lost by the reset is added back.

### The blind spot
The rule fires **only on a decrease**. In the example `200 → 300` went **up**, so a restart that climbed back *above* the previous sample within one interval is **undetected**. If the counter really went `200 → 5000 → crash → 0 → 300`, the true increase for that step is ~5100 but Prometheus records **100** — a silent undercount. **Implication:** scrape high-QPS / crash-prone services more frequently, so less traffic can accumulate-then-vanish inside one interval.

## Push: delta vs cumulative

- **Classic StatsD (per-flush deltas).** The app sends `requests:1|c` per event; the sidecar sums arrivals in the flush window and pushes the **increment**, then zeroes its accumulator. Nothing cumulative crosses the wire, so **nothing goes negative**. A restart instead produces a **short or missing flush** → downstream sees a **silent dip/gap**. This is *worse* than pull in one way: there's no reset signal to correct against — the number is just lower.
  - *App restart:* pure StatsD sends each event immediately → only un-sent in-flight packets lost (near-zero); a batching client loses its buffer.
  - *Agent restart:* everything in the current unflushed window is lost, and UDP arriving during downtime is dropped (no listener).
- **Cumulative-counter agents.** Some agents maintain a monotonic counter and push absolute values → an agent restart resets it to 0, and the TSDB applies the **same correction as Prometheus, with the same blind spot.**

## Percentiles: the asymmetry (why they can't be recovered)

Per-window percentiles/sketches are **not cumulative** — the buffer/sketch empties each flush, so there is no monotonic value and **nothing to reset-correct**. A lost window's distribution is **unrecoverable**, and the loss is **adversarial**: crashes correlate with slow / error / timing-out requests, so the samples most likely dropped are exactly the tail → p99 looks **artificially good** right when things were worst.

### Worked example (10s flush windows)
```
W1 (0–10s):  agent buffers 50,000 latencies → flush p99 = 95 ms          ✓
W2 (10–20s): agent CRASHES at t=17.
   - t=10..17 (~35,000 values) held in the sketch are LOST (never flushed)
   - t=17..18 UDP dropped; agent restarts, buffers only t=18..20 (~10,000, calm)
   → flush p99 computed over calm-period samples only = 60 ms   ✗ wrong, biased LOW
W3 (20–30s): back to normal → p99 = 95 ms                                 ✓
```
No `(last − first) + correction` exists here — the information is simply gone.

## What buys back graceful degradation
1. **Mergeable sketches + fleet-merge.** If all instances push DDSketches and one agent dies for a window, the fleet p99 is merged from the surviving 9/10 — a slight bias, not a blackout. (Per-host-percentile designs lose that host-window entirely.) See [[system-design-concepts/mergeable-metrics-and-quantiles]].
2. **The pull symmetry (nice unifier).** A Prometheus histogram is just a set of **cumulative bucket counters**, so `rate(_bucket[5m])` inherits the counter-reset correction **bucket by bucket** for free — with the same "hidden by fast climb-back" blind spot. Push per-flush percentiles get none of this.

## Key points
- **Cumulative representations are correctable; per-window ones aren't.** Pull counters and pull histogram buckets survive restarts via reset correction; per-flush percentile pushes cannot.
- **`rate()` corrects only on an observed decrease** — a restart followed by a fast climb-back within one interval is invisible → undercount. Shorter intervals shrink the loss.
- **Delta-push turns a restart into a silent gap**, not a negative counter — no reset signal at all.
- **Percentile loss is tail-biased** because crashes correlate with the pathological requests — the metric fails you exactly when it matters.

## Interview angle
> "Prometheus counters reset to zero on restart, and `rate()` handles it: when the sample drops it assumes the counter fell from its last value to zero and adds that back — `(last − first) + Σ pre-drop values`. The catch is it only triggers on a *decrease*; if a service restarts and climbs back above the previous sample within one scrape interval, the reset is invisible and I undercount — so I scrape crash-prone high-QPS services more often. In push, delta-based StatsD never goes negative; a restart is a silent missing flush instead. And percentiles are the real asymmetry: they're per-window, so a lost window is unrecoverable, and because crashes correlate with slow requests the loss is biased toward the tail. I lean on mergeable sketches with fleet-level merge so one dead agent just drops to 9-of-10 contributors instead of a blackout."

## Connections
- [[system-design-concepts/red-metrics-exposition]] — the counters and histograms whose reset behavior this page explains
- [[system-design-concepts/mergeable-metrics-and-quantiles]] — bucket counters inherit reset recovery; the sketch-merge that degrades gracefully
- [[system-design-concepts/metrics-pull-vs-push]] — delta-push vs cumulative-push restart behavior
- [[theory/durability-rpo-rto]] — un-flushed in-memory aggregates are an RPO gap; the crash-recovery / non-idempotent-replay family
- [[system-design-concepts/exactly-once-semantics]] — at-least-once delivery vs the lost-window data gap; different failure than duplication

## Sources
- [[sources/docs/metrics-collection-observability-self-study]] — §4 counter reset math, blind spot, push delta-vs-cumulative, percentile asymmetry
</content>
