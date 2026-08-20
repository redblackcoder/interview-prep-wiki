---
source: docs/streaming-101-world-beyond-batch.md
source_url: https://github.com/redblackcoder/interview-prep-raw/blob/master/docs/streaming-101-world-beyond-batch.md
author: Tyler Akidau
type: doc
date_extracted: 2026-08-19
topic: system-design-concepts
---

# Streaming 101: The World Beyond Batch

## Key Ideas
- **Terminology discipline.** "Streaming" = an execution engine designed for *unbounded* data —
  nothing more. Separate that from "unbounded data," "unbounded processing," and
  "approximate/low-latency results" (which are properties, not engines).
- **A well-designed streaming system is a strict superset of batch.** To beat batch you need
  two things: (1) **correctness** (= strongly-consistent checkpointed state → exactly-once),
  and (2) **tools for reasoning about time.**
- **Event time vs. processing time.** Event time = when it happened; processing time = when it's
  observed. Skew between them is non-zero and variable (network, contention, mobile devices in
  airplane mode). This is *the* core distinction.
- **Window by event time, not processing time**, if you care about correctness — else late/skewed
  data lands in the wrong window. Windowing strategies: fixed, sliding, sessions (dynamic).
- **The completeness problem.** For unbounded data you can't know you've seen all events for a
  window. Use **watermarks** as a heuristic estimate of completeness — but for exact use cases
  (billing), let the pipeline explicitly say when to materialize and how to refine on late data.
- **Kappa > Lambda (Akidau's stance).** Since streaming ⊇ batch, the dual-pipeline Lambda should
  give way to a single replayable stream pipeline (echoing Kreps' "Questioning the Lambda Architecture").

## My Understanding
- I derived the event-time/processing-time distinction *live* in the counter mock: the dashboard
  freshness SLA is a **processing-time** guarantee ("reflected within N minutes of arrival"), while
  buckets are keyed by **event time**, which is exactly why a past dashboard number can change when a
  straggler lands. This article gives me the vocabulary for what I already reasoned out.
- **Watermarks + allowed-lateness** is the named mechanism behind the "5-day grace window" I invented
  for billing — the depth-miss the interviewer flagged (I described the behavior, didn't name the tool).
- Correctness = strongly-consistent checkpointed state → this is the *other half* of "no silent loss":
  the consumer-crash story (commit offset after the write lands), which I missed in the mock.

## Open Questions
- Concrete watermark implementation (perfect vs. heuristic watermarks; how much lateness to allow) —
  need the Dataflow Model (Streaming 102) for the triggers/accumulation model.
- Is Kappa always achievable in practice, or does the batch layer survive for reprocessing huge
  historical corrections? (Real systems seem to keep a batch escape hatch.)

## Connections
- Contradicts/nuances: [[sources/docs/how-to-beat-the-cap-theorem]] — this is the Kappa rebuttal to
  that Lambda thesis.
- Builds on: [[sources/docs/the-log-jay-kreps]] — "log is another word for stream"; replay + checkpointed
  local state is Kreps' table/log duality for stateful processing.
- Relates to: [[sources/docs/kafka-101-bytebytego]] — Kafka Streams' exactly-once + local state stores.
- Anchors wiki pages: [[wiki/system-design-concepts/event-time-vs-processing-time]],
  [[wiki/system-design-concepts/lambda-vs-kappa]].
