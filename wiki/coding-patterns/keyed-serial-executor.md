# Keyed Serial Executor (Bounded, Concurrent, Graceful-Shutdown)

A classic concurrency interview problem — "KeyedTaskExecutor": accept tasks under a key, run same-key tasks **serially in order**, cap global concurrency and total in-flight work, reject when full or shutting down, isolate failures, and drain on shutdown. It looks like five hard problems tangled together; the whole skill is **decomposing it into independent mechanisms, one concurrency construct per requirement** (see [[theory/concurrency-constructs]]). The winning shape is **per-key serial queue + shared bounded pool + atomic admission + completion-driven hand-off** — not a polling dispatcher.

## Problem

`submit(key, task)` callable from many threads at once, satisfying:

1. Thread-safe under overlapping `submit`.
2. Same key → **serial, in accepted order**.
3. **At most 4** tasks execute concurrently (global).
4. **At most 1000** queued-or-running tasks globally; reject when full.
5. **No task accepted after shutdown begins** (or when full).
6. `submit` returns whether the task was **accepted**.
7. A **failing task must not block** later tasks.
8. `shutdown(timeout)` stops accepting and **drains accepted work up to the timeout**.

## The decomposition (the actual insight)

| Requirement | Mechanism |
|---|---|
| ≤4 concurrent (3) | `newFixedThreadPool(4)` — the pool *is* the concurrency bound |
| ≤1000 + reject-when-full (4) + reject-after-shutdown (5) + report (6) | `AtomicInteger` CAS **admission loop** guarded by a `volatile boolean shuttingDown` |
| Same-key serial in order (1,2) | `ConcurrentHashMap` → per-key `ArrayDeque` + a `scheduled` flag; **one runner per key**, re-scheduled on completion |
| Failure isolation (7) | `try/catch(Throwable)` around `task.run()` |
| Drain-with-timeout (8) | decrement-to-zero **signal** + timed `wait`, then `shutdownNow()` |

The two ideas that make it click:
- **Admission is a CAS loop, not increment-then-check.** "Not full AND not shutting down" must be one atomic decision, or two concurrent submits both pass the check and overshoot the cap.
- **Serialization is "one runner per key," hand-off on completion.** A finishing task, under the key's lock, either re-schedules the next task for that key or tears the key down. No dispatcher thread, no polling.

## Complete code

```java
import java.util.ArrayDeque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Runs same-key tasks serially in accepted order, ≤4 concurrently, ≤1000 in flight. */
public final class KeyedTaskExecutor {

    private static final int MAX_CONCURRENCY = 4;
    private static final int MAX_INFLIGHT    = 1000;   // queued + running

    /** One worker thread per permit: the pool IS the ≤4 concurrency bound (req 3). */
    private final ExecutorService pool = Executors.newFixedThreadPool(MAX_CONCURRENCY);

    /** Pending tasks for one key, plus whether a runner is currently active for it. */
    private static final class KeyQueue {
        final ArrayDeque<Runnable> tasks = new ArrayDeque<>();
        boolean scheduled = false;   // a runner is queued/running for this key
        boolean removed   = false;   // torn down; late submitters must re-create
    }

    private final ConcurrentHashMap<Object, KeyQueue> queues = new ConcurrentHashMap<>();
    private final AtomicInteger inflight = new AtomicInteger(0);   // admission counter
    private volatile boolean shuttingDown = false;
    private final Object idle = new Object();                      // signalled at inflight==0

    /** @return true if accepted for execution; false if rejected (full or shutting down). */
    public boolean submit(Object key, Runnable task) {
        // --- Admission: atomic "not shutting down AND not full" via a CAS loop ---
        while (true) {
            if (shuttingDown) return false;                 // req 5
            int n = inflight.get();
            if (n >= MAX_INFLIGHT) return false;            // req 4
            if (inflight.compareAndSet(n, n + 1)) break;    // won a slot
        }

        // --- Enqueue under the key in accepted order; start a runner if none active ---
        while (true) {
            KeyQueue q = queues.computeIfAbsent(key, k -> new KeyQueue()); // atomic get-or-create
            synchronized (q) {
                if (q.removed) continue;                    // racing teardown -> retry with a fresh q
                q.tasks.addLast(task);                      // FIFO == accepted order (req 2)
                if (q.scheduled) return true;               // an active runner will pick it up
                q.scheduled = true;                         // we own the (re)start
            }
            pool.execute(() -> drain(key, q));
            return true;
        }
    }

    /** Runs exactly one task for the key, then re-schedules the next (serial per key). */
    private void drain(Object key, KeyQueue q) {
        Runnable task;
        synchronized (q) { task = q.tasks.pollFirst(); }

        try {
            task.run();
        } catch (Throwable t) {
            // Isolate failures so later same-key tasks still run (req 7). Real code: log t.
        } finally {
            if (inflight.decrementAndGet() == 0) {          // free the slot (req 4/8)
                synchronized (idle) { idle.notifyAll(); }
            }
            boolean more;
            synchronized (q) {
                more = !q.tasks.isEmpty();
                if (!more) {                                // key drained: tear it down
                    q.scheduled = false;
                    q.removed   = true;
                    queues.remove(key, q);                  // value-equality removal (cleanup)
                }
            }
            if (more) {
                try {
                    pool.execute(() -> drain(key, q));      // next task for this key, still serial
                } catch (RejectedExecutionException rej) {
                    // Pool hard-stopped after shutdown timeout; drop this key's remaining work.
                }
            }
        }
    }

    /** Stop accepting, drain already-accepted work up to the timeout, then hard-stop. */
    public void shutdown(long timeout, TimeUnit unit) {
        shuttingDown = true;                                // req 5: no new admissions
        long deadlineNanos = System.nanoTime() + unit.toNanos(timeout);
        synchronized (idle) {
            long remainingMillis;
            while (inflight.get() > 0
                   && (remainingMillis =
                         TimeUnit.NANOSECONDS.toMillis(deadlineNanos - System.nanoTime())) > 0) {
                try {
                    idle.wait(remainingMillis);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        pool.shutdownNow();                                 // interrupt whatever remains (req 8)
    }
}
```

## Why each subtle bit is there

- **`computeIfAbsent` + the `removed`/retry loop** close the get-or-create *and* the teardown race: if a submitter grabbed a `KeyQueue` that a finishing runner is about to remove, it sees `removed == true` and loops to create a fresh one — the task is never lost or appended to a dead queue.
- **`scheduled` flag** guarantees exactly one runner per key at a time = serialization. The runner, not the submitter, decides whether to continue — that's the completion-driven hand-off.
- **`pool.execute` is called outside the `q` lock** — never submit to a pool while holding a lock (deadlock/latency hazard).
- **Decrement lives in `finally`** — the single most common bug is forgetting it, after which `inflight` climbs to 1000 and rejects everything forever.
- **`shutdownNow()` after the drain window** is the "up to the timeout" clause: accepted work finishes if it can, then we interrupt.

## Common bugs (seen in the live attempt)

- **No real concurrency bound** — a single polling dispatcher (`sleep(100); scan map`) serializes dispatch and never enforces "≤4". Use a fixed pool / semaphore + completion hand-off.
- **Never decrementing the counter** → permanent rejection at the cap.
- **No `try/catch` around `task.run()`** → one throwable stalls the key's chain (violates req 7).
- **Per-key map entries never removed** → memory leak; naive "if empty remove" collides with a concurrent submit (needs the `removed`/retry guard).
- **`sleep(timeout)` for shutdown** → doesn't actually drain; wait on a done-signal or the deadline instead.
- **`getAndIncrement` then compare** for admission → overshoots the cap under concurrent submits.

## Variants worth naming out loud

- **`Semaphore(4)` + cached pool** instead of `newFixedThreadPool(4)` — decouples permit count from thread count; mention the trade-off (fixed pool is simpler; semaphore is more flexible).
- **Per-key `CompletableFuture` chain** — `map.compute(key, (k, prev) -> (prev == null ? completed : prev).thenRunAsync(task, pool))`. Elegant serialization, but bounding global concurrency and total in-flight then needs a separate semaphore/counter, and failure isolation needs `.exceptionally`/`handle` so one failure doesn't poison the chain.
- **Fairness across keys** — the fixed pool is roughly FIFO across ready runners; if strict cross-key fairness is required, front it with an explicit ready-queue. Not required by the spec.

## Interview angle

> "I decompose it into one construct per requirement instead of one big lock. Concurrency ≤4 is a fixed pool of 4 — the pool is the semaphore. The ≤1000-and-reject-when-full-or-shutdown rule is an `AtomicInteger` CAS admission loop guarded by a volatile flag, because check-then-act on a bound is itself a race. Per-key serialization is a `ConcurrentHashMap.computeIfAbsent` to a per-key deque with a `scheduled` flag, and — this is the key move — the *finishing* task, under the key's lock, re-schedules the next one. That completion-driven hand-off replaces any polling dispatcher. Failures are isolated with a `try/catch` around `run()`, the counter is decremented in `finally`, and shutdown waits on a drain signal until a deadline, then `shutdownNow()`."

## Connections
- [[theory/concurrency-constructs]] — the toolkit each requirement maps onto (CAS loop, semaphore/pool, `computeIfAbsent`, wait/notify)
- [[theory/concurrency-primitives]] — the thread/pool execution model underneath the executor
- [[system-design-concepts/dispatch-and-matching]] — the same atomic check-and-claim idea (first-accept-wins behind a two-invariant claim) at the distributed layer
- [[system-design-concepts/work-distribution]] — per-key serialization is single-machine work partitioning; this is its in-process analog
- [[system-design-concepts/hot-key-write-contention]] — a hot key here serializes onto one runner, the in-process face of hot-key contention

## Sources
- [[sources/docs/azure-storage-interview-loop]] — Part 2: the KeyedTaskExecutor problem statement and the live attempt this pattern corrects
- Runnable code in raw: [keyed-task-executor/](https://github.com/redblackcoder/interview-prep-raw/blob/master/code/keyed-task-executor/)
</content>
