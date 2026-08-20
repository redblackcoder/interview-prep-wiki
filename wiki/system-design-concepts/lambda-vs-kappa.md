# Lambda vs Kappa Architecture

Two ways to serve **both** low-latency approximate results **and** exact, reproducible results from the same data. **Lambda** (Marz) runs a batch layer and a realtime layer in parallel and merges them. **Kappa** (Kreps/Akidau) argues a well-designed streaming system is a superset of batch, so you run **one** replayable stream pipeline and reprocess by replaying the log. This is the exact tension behind the "dashboard-approximate / billing-exact" dual-SLA counter.

## The one-sentence mental model

> **Lambda: two pipelines (batch = exact-but-late, realtime = fast-but-approximate), merged at query time, and the batch layer eventually overrides the realtime layer's mistakes. Kappa: one stream pipeline over an immutable log; to "recompute," you replay the log — no second codebase.**

## How Lambda works (Marz, "How to Beat the CAP Theorem")

The real source of CAP pain isn't CAP — it's **mutable state + incremental updates** (which forces read-repair, vector clocks, divergence). Remove mutability:

- **`Query = Function(All Data)`** over an **immutable, append-only master dataset**. CRUD becomes **CR** (update = append a newer fact; delete = append a tombstone).
- **Batch layer:** precompute views over all-but-the-last-few-hours (e.g. Hadoop → read-only serving DB). Exact, reproducible, but hours stale.
- **Realtime layer:** compensate for the recent window with incremental algorithms (e.g. Storm + Cassandra). Fast, approximate.
- **Query = merge(batch view, realtime view).**

The elegance: **realtime complexity is transient** — the batch layer constantly overrides it, so a bug or an approximation in the realtime layer *self-heals* on the next batch pass ("eventual accuracy"). And **human fault-tolerance** is maximal: bad code or bad data → fix and **recompute** from the immutable master dataset.

## How Kappa answers (Kreps/Akidau, "Streaming 101")

Maintaining **two codebases** (batch + realtime) and merging them is the tax Lambda charges. Akidau's claim: a streaming engine with (1) **correctness** (strongly-consistent checkpointed state → exactly-once) and (2) **tools for reasoning about time** (see [[system-design-concepts/event-time-vs-processing-time]]) is a *strict superset* of batch. So:

- Keep **one** stream pipeline over a replayable log.
- To reprocess (bug fix, new view), **replay the log** from the start into a new version of the job, then cut over — no separate batch system.

```
 LAMBDA                                   KAPPA
 events ─┬─▶ batch layer  ─▶ exact view    events ─▶ [replayable log] ─▶ stream job ─▶ view
         └─▶ speed layer ─▶ approx view                 └── reprocess = replay from offset 0
              merge(exact, approx) → answer
```

## The decision table

| Dimension | Lambda | Kappa |
|---|---|---|
| **Pipelines / codebases** | two (batch + realtime), merged | one (stream) |
| **Exactness** | from the batch recompute | from replay + exactly-once stream state |
| **Reprocessing** | rerun batch job | replay the log from an offset |
| **Operational burden** | high (two systems, merge logic, seam bugs) | lower (one system) but needs a durable replayable log + big retention |
| **Latency** | realtime layer covers the recent window | native low-latency stream output |
| **Best when** | batch & stream stacks already differ; huge historical corrections | replayable log exists; want one codebase |

## Key points
- **Both rest on an immutable, replayable log** as the master dataset ([[system-design-concepts/the-log-abstraction]]) — they differ only in whether you keep a *separate* batch recompute.
- The **"exact = recompute over the immutable log"** insight is shared by both — it's why exactness is a *batch/replay property*, not an online-dedup property (the key unlock in the tagged-counter mock).
- Lambda's defining virtue is **human fault-tolerance via recompute**; its defining cost is **dual code + merge seam** (double-counting a late event at the batch/realtime boundary is the classic bug).
- Kappa's cost moves to the **log**: you need enough retention to replay history, and genuine **exactly-once** stream state ([[system-design-concepts/exactly-once-semantics]]).
- Real systems often keep a **batch escape hatch** for massive historical reprocessing even when nominally Kappa.

## Interview angle

> "Both solve the same problem — serve fast-approximate and exact-reproducible from one dataset — and both assume an immutable, replayable log as the source of truth. Lambda runs two pipelines: a batch layer that recomputes exact views and a realtime layer that covers the last few hours approximately, merged at query time; the batch layer overrides the realtime layer, so realtime mistakes self-heal. The cost is two codebases and a merge seam. Kappa says a good streaming engine — with exactly-once state and event-time tooling — is a superset of batch, so run one stream pipeline and 'recompute' by replaying the log. I'd map it straight to the tagged-counter: the dashboard is the realtime/approximate layer, billing is the batch/replay exact layer, and the key realization is that exactness comes from recomputing over the immutable log, not from a giant online dedup set."

## Connections
- [[system-design-concepts/the-log-abstraction]] — the immutable replayable log both architectures are built on
- [[system-design-concepts/event-time-vs-processing-time]] — Kappa's viability hinges on event-time + watermarks
- [[system-design-concepts/exactly-once-semantics]] — Kappa needs exactly-once stream state to match batch exactness
- [[system-design-concepts/table-log-duality]] — "recompute the view" = fold the log into a table
- [[theory/consistency-models]] — Lambda delivers eventual consistency *without* read-repair by using immutable data
- [[system-design-concepts/read-state-watermarking]] — sibling "approximate-now, exact-later" reconciliation pattern

## Sources
- [[sources/docs/how-to-beat-the-cap-theorem]] — Nathan Marz's Lambda thesis (immutable master dataset, batch+realtime, human fault-tolerance)
- [how-to-beat-the-cap-theorem.md](https://github.com/redblackcoder/interview-prep-raw/blob/master/docs/how-to-beat-the-cap-theorem.md) — Nathan Marz (2011)
- [[sources/docs/streaming-101-world-beyond-batch]] — Akidau's Kappa rebuttal ("streaming ⊇ batch")
- [streaming-101-world-beyond-batch.md](https://github.com/redblackcoder/interview-prep-raw/blob/master/docs/streaming-101-world-beyond-batch.md) — Tyler Akidau (2015)
