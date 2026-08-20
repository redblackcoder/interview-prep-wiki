# Event Time vs Processing Time

The core distinction for any system that aggregates events over time: **event time** = when the thing actually happened; **processing time** = when your pipeline observed it. They are never equal, the skew is variable and unbounded (a phone in airplane mode uploads days late), and conflating them silently corrupts any time-windowed result. This is the distinction that decides whether a dashboard number can *change after you looked at it*, and whether monthly billing can ever be exact.

## The one-sentence mental model

> **Bucket by event time (when it happened), not processing time (when it arrived) — otherwise late/out-of-order data lands in the wrong window and your answer is wrong; then use watermarks to *estimate* when a window is complete, and an explicit late-data policy for the cases where "estimate" isn't good enough (billing).**

## How it works

Skew comes from network congestion, contention, and especially input characteristics — the canonical example is *"a plane full of people taking phones out of airplane mode after a flight,"* flushing an offline backlog with event times hours/days old.

Two consequences fall straight out:

- **Freshness is a processing-time guarantee:** "reflected within N minutes of *arrival*." (The dashboard's "tens of minutes" SLA.)
- **Correctness requires event-time bucketing:** a late event must flow into *its own* event-time window. Which means **a window you already reported can change** when a straggler lands — acceptable for dashboards (eventually-consistent views), unacceptable for billing without a cutoff.

```
 processing time ─────────────────────────▶ (arrival)
   event at t=10:00 arrives at 10:00:05  ✔ on time
   event at t=10:00 arrives at 14:20     ← late; belongs in the 10:00 window,
                                            not the 14:00 window it "arrived" in
```

### Windowing strategies
- **Fixed** (tumbling): uniform non-overlapping buckets (hourly counts).
- **Sliding:** fixed length + fixed period; overlap if period < length.
- **Sessions:** dynamic — a burst of activity terminated by an inactivity gap; length depends on the data (per-user), so inherently event-time.

### The completeness problem → watermarks
For unbounded, out-of-order data you can **never know for certain** you've seen all events for window X. A **watermark** is a heuristic estimate of event-time completeness ("probably seen everything up to time T"). Firing on the watermark gives you a timely answer; **allowed lateness** lets you keep updating the window as stragglers arrive. For **absolute correctness (billing)** you don't rely on the heuristic — you set an explicit **cutoff/grace window** and define how results are **refined** when late data arrives (corrections/adjustments).

## Key points
- **Windowing by processing time is simpler** (perfect completeness knowledge, no late data) and correct *only* if events arrive in event-time order — which distributed/mobile sources violate. Fine for "requests per second right now" monitoring; wrong for "activity that happened at time X."
- The named mechanism behind an ad-hoc "5-day grace window" is **watermarks + allowed-lateness**; saying the mechanism (not just the behavior) is the depth signal in an interview.
- Dashboards tolerate **mutable, eventually-accurate** windows; billing needs a **cutoff + correction policy** — same event log, two consumers, two completeness models (the [[system-design-concepts/lambda-vs-kappa|Lambda/Kappa]] split).
- Approximation algorithms with error bounds assume in-order arrival — those bounds are meaningless on skewed, out-of-order data. Beware.

## Interview angle

> "Event time is when the event happened; processing time is when my pipeline saw it. They diverge by a variable, sometimes huge amount — think a phone flushing a day of offline events. If I care about *when things happened* — billing, activity analytics — I must bucket by event time, or late events land in the wrong window and the numbers are wrong. The catch is completeness: with out-of-order data I can never be sure I've seen everything for a window, so I use a watermark as a heuristic 'probably complete up to T,' fire on it, and allow lateness to refine the window. For a dashboard that's fine — the number can shift as stragglers arrive. For billing I don't trust the heuristic: I set an explicit cutoff and a correction policy for anything later. That's also why the same event stream feeds two consumers with different completeness models — the freshness SLA is really a processing-time guarantee, but the buckets are event-time."

## Connections
- [[system-design-concepts/lambda-vs-kappa]] — the dashboard(approx)/billing(exact) dual-SLA is this distinction operationalized
- [[system-design-concepts/exactly-once-semantics]] — "no silent loss" also needs the consumer-crash story (checkpointed offsets), the other half of correctness
- [[system-design-concepts/read-state-watermarking]] — "watermark" reused: there a per-user read position, here an event-time completeness estimate
- [[tech/kafka]] — Kafka Streams / stream processors implement event-time windowing + watermarks
- [[system-design-concepts/the-log-abstraction]] — replaying the log by event time is how you recompute exact windowed views
- [[theory/latency-numbers]] — the physical skews (WAN, queueing) that make processing time lag event time

## Sources
- [[sources/docs/streaming-101-world-beyond-batch]] — event vs processing time, windowing strategies, watermarks, the completeness problem
- [streaming-101-world-beyond-batch.md](https://github.com/redblackcoder/interview-prep-raw/blob/master/docs/streaming-101-world-beyond-batch.md) — Tyler Akidau, "Streaming 101" (2015)
