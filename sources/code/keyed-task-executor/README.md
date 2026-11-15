# KeyedTaskExecutor

Solution to the "KeyedTaskExecutor" concurrency interview problem.

Accept `submit(key, task)` from many threads and:
1. thread-safe under overlapping submits,
2. same key → serial, in accepted order,
3. ≤4 tasks concurrent (global),
4. ≤1000 queued-or-running; reject when full,
5. reject after shutdown begins,
6. `submit` reports accepted/rejected,
7. a failing task must not block later tasks,
8. `shutdown(timeout)` drains accepted work up to the timeout.

## Design
Per-key serial queue + shared bounded pool + atomic CAS admission + completion-driven hand-off:
- `newFixedThreadPool(4)` — the pool *is* the concurrency bound.
- `AtomicInteger` CAS admission loop + `volatile boolean shuttingDown` — the ≤1000 / reject rules.
- `ConcurrentHashMap<key, KeyQueue>` with a `scheduled` flag — one runner per key; the finishing task re-schedules the next.
- `try/catch` around `run()` — failure isolation.
- decrement-to-zero signal + timed `wait`, then `shutdownNow()` — graceful drain.

Pattern write-up (with the requirement→mechanism mapping and common bugs) lives in the wiki:
`wiki/coding-patterns/keyed-serial-executor.md`.

## Run
```
javac KeyedTaskExecutor.java
java KeyedTaskExecutor      # smoke test: same-key ordering, cross-key concurrency, failure isolation
```
Requires JDK 8+. `main` is a small demo, not a test suite.
</content>
