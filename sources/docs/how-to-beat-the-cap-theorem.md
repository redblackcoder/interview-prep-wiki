---
source: docs/how-to-beat-the-cap-theorem.md
source_url: https://github.com/redblackcoder/interview-prep-raw/blob/master/docs/how-to-beat-the-cap-theorem.md
author: Nathan Marz
type: doc
date_extracted: 2026-08-19
topic: system-design-concepts
---

# How to Beat the CAP Theorem (Lambda Architecture)

## Key Ideas
- **You can't beat CAP, but you can isolate its complexity.** The pain isn't CAP itself —
  it's the interaction of CAP with **mutable state + incremental updates** (which forces
  read-repair, vector clocks, divergent replicas).
- **Query = Function(All Data).** Reframe every data system as a pure function over an
  immutable, append-only master dataset. Immutable data has **no update**, so replicas
  can't diverge — the CAP-induced complexity evaporates.
- **CRUD → CR.** "Update" = append a newer fact; "delete" = append a tombstone fact. Data
  is time-stamped and true-forever.
- **Human fault-tolerance** is the fault-tolerance that matters most. With an immutable
  master dataset + pure query functions, recovery from a bug or bad write is: fix code /
  delete bad data, then **recompute**. Mutable DBs destroy the old value on update — no
  recovery path.
- **Batch layer + realtime layer.** Batch precomputes views over all-but-the-last-few-hours
  (Hadoop → read-only serving DB); a realtime layer compensates for the recent window
  (Storm + Cassandra). Query = merge(batch view, realtime view).
- **Realtime complexity is transient.** Because the batch layer eventually overrides it,
  mistakes in the realtime layer self-heal → "eventual accuracy." Exact algorithm on batch,
  approximate on realtime.

## My Understanding
- This is the skeleton under my counter mock. My **dashboard-approximate / billing-exact**
  split is Marz's **realtime-layer / batch-layer** split, one-to-one.
- The insight I was one step from in the interview is Marz's whole thesis: **exactness is a
  property of recomputation over an immutable log, not of online dedup.** Billing = recompute
  `distinct(event_id)` over the append-only master dataset. That's why I didn't actually need
  a giant global dedup set for the exact path.
- "Mutability is just an inflexible form of garbage collection" reframed how I think about
  updates — the default DB behavior is silently the *lossy* choice.

## Open Questions
- Lambda's cost is maintaining **two codebases** (batch + realtime) and merging them. When is
  that cost worth it vs. going Kappa (single stream pipeline, replay to recompute)? — resolved
  partly by [[sources/docs/streaming-101-world-beyond-batch]].
- Practical merge semantics when a late event crosses the batch/realtime boundary — how do you
  avoid double-counting at the seam?

## Connections
- Builds on: [[sources/docs/the-log-jay-kreps]] — the immutable master dataset *is* the log;
  "recompute from raw" is replaying it.
- Contradicts/nuances: [[sources/docs/streaming-101-world-beyond-batch]] — Akidau argues a
  well-designed streaming system is a *superset* of batch, so the dual pipeline (Lambda) should
  give way to Kappa. This page is the thesis; Streaming 101 is the rebuttal.
- Relates to: [[wiki/theory/consistency-models]] — eventual consistency without read-repair.
- Anchors wiki page: [[wiki/system-design-concepts/lambda-vs-kappa]].
