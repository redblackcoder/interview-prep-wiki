---
source: interview session (raw transcript not published)
type: doc
date_extracted: 2026-09
topic: Interview Prep Wiki
secondary_source: code/keyed-task-executor/ (https://github.com/redblackcoder/interview-prep-raw/blob/master/code/keyed-task-executor/)
---

# KeyedTaskExecutor — Concurrency Coding Round

A live concurrent-programming exercise (KeyedTaskExecutor) from a coding round. Extracted to seed one concurrency-constructs page and one coding-pattern page.

## Key Ideas

**Coding (KeyedTaskExecutor):** bounded concurrent executor — same-key serial in accepted order, ≤4 concurrent, ≤1000 in flight, reject when full / after shutdown, failure isolation, drain-with-timeout shutdown. Clean design: **per-key serial queue + shared bounded pool + atomic CAS admission + completion-driven hand-off**.

## My Understanding

- CAS admission, per-key serialization, and tracking which keys are running are the right building blocks, but a **polling dispatcher** is the wrong shape. Use **completion-driven hand-off**: the *finishing* task schedules the key's next task.
- The details that must be automatic: decrement the counter in `finally`, wrap `run()` in `try/catch`, and never `sleep()` for shutdown — wait on a drain signal.

## Open Questions

- KeyedTaskExecutor: how would the design change if strict cross-key fairness (not just per-key order) were required? (Sketch: front the pool with an explicit ready-queue.)

## Connections

- Relates to: [[wiki/theory/concurrency-constructs]] — the toolkit the coding problem decomposes into
- Relates to: [[wiki/coding-patterns/keyed-serial-executor]] — the worked problem + complete code
