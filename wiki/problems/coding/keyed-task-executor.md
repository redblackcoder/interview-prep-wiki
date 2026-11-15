# Keyed task executor

## Problem

Implement `submit(key, task)` for callers that submit work from several threads. Each call returns whether the executor accepted the task. Tasks accepted for the same key must run one at a time in FIFO order. Tasks with different keys may overlap.

The executor has two independent limits. At most **four tasks may run** at once across all keys. At most **1,000 tasks may be outstanding**, counting both queued and running tasks. If all 1,000 slots are occupied, reject the next submission. A task that throws must release its slot and allow later tasks for its key to run.

Implement `shutdown(timeout)` as well. Once shutdown begins, reject new submissions and give accepted work up to the timeout to finish.

## Trace a submission

Suppose the executor accepts `A1`, `A2`, then `B1`, where the letter is the key. All three count toward the 1,000-task limit. A worker can start `A1` and another can start `B1`. Even with two workers free, `A2` must wait for `A1`. If `A1` throws, `A2` still gets its turn. There is no required ordering between `B1` and either A task.

## Recorded implementation

The Java implementation uses a fixed pool of four workers. On submission it reserves an outstanding slot with an atomic counter, looks up the key's queue, then appends the task under that queue's lock. The first submitter for an idle key marks it scheduled and hands a runner to the pool. Later submitters append to the same queue.

Each runner takes one task and executes it outside the queue lock. Its cleanup releases the outstanding slot even if the task throws. If the queue has more work, the runner schedules its successor. Otherwise it removes the idle queue. This completion-driven handoff is what lets B make progress while preserving A's order.

## Review the shutdown boundary

The saved code checks the shutdown flag separately from reserving a slot. A submitter can read the flag, pause, and resume after shutdown has observed zero outstanding tasks and stopped the pool. Its first dispatch can then throw instead of returning an acceptance result. The included demo exercises task ordering and failure isolation; it does not exercise this interleaving.

[Saved Java implementation and demo](../../../sources/code/keyed-task-executor/KeyedTaskExecutor.java) · [Keyed serial executor pattern](../../coding-patterns/keyed-serial-executor.md)
