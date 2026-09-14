# Concurrency Constructs: The Synchronization Toolkit (JVM-flavored)

The set of building blocks you compose to make concurrent code correct. The mistake most people make is memorizing the API surface; the skill is **mapping each construct to the exact problem it solves** — mutual exclusion, safe publication, bounded resources, coordination, or lock-free state — and knowing which is the *cheapest* tool that still closes the race. Get that mapping and a problem like [[coding-patterns/keyed-serial-executor]] decomposes into "one construct per requirement."

This page is the toolkit. [[theory/concurrency-primitives]] covers the *execution units* (process/thread/green thread) these run on.

## The five problems and their tools

| Problem | "I need to…" | Tools |
|---|---|---|
| **Safe publication / visibility** | make one thread's writes visible to another | `volatile`, `final`, happens-before, atomics, any lock |
| **Mutual exclusion** | let only one thread touch state at a time | `synchronized`, `ReentrantLock`, `ReadWriteLock`, `StampedLock` |
| **Lock-free atomic state** | update a counter/flag without a lock | `AtomicInteger/Long/Reference`, CAS, `LongAdder` |
| **Bounded resource / permits** | cap how many can proceed at once | `Semaphore`, a fixed thread pool |
| **Coordination / signaling** | wait until a condition or other threads are ready | `wait/notify`, `Condition`, `CountDownLatch`, `CyclicBarrier`, `Phaser`, `BlockingQueue`, `Future`/`CompletableFuture` |

## 1. Visibility: the thing beginners miss

Threads can cache fields in registers/CPU caches; without a **happens-before** edge, a write on thread A may *never* be seen by thread B. Correctness isn't just "no torn writes" — it's visibility + ordering.

- **`volatile`** — every read sees the latest write; establishes happens-before across the read/write. Use for a `boolean shuttingDown` flag or a published reference. It does **not** make compound actions atomic (`count++` on a volatile is still a race).
- **`final`** — safe publication of immutable objects: fields are visible once the constructor completes.
- **Locks and atomics** also create happens-before edges, so anything you touch under a lock is visible to the next holder.

> Reaching for `volatile` to make `count++` safe is the classic error — that's a *read-modify-write*, which needs an atomic or a lock.

## 2. Mutual exclusion

- **`synchronized`** (monitor) — simplest; reentrant; auto-released on exception. No tryLock, no fairness, no interruptible acquire.
- **`ReentrantLock`** — same semantics plus `tryLock(timeout)`, `lockInterruptibly()`, and optional fairness. Costs you a `try/finally unlock()`. Pairs with `Condition` for wait-sets.
- **`ReadWriteLock`** — many readers *or* one writer; wins only when reads vastly dominate and the critical section is non-trivial (otherwise the bookkeeping overhead loses).
- **`StampedLock`** — adds **optimistic reads** (validate a stamp instead of locking); big win for read-heavy structures, but not reentrant and easy to misuse.

**Golden rules:** hold locks briefly, never call foreign/blocking code (or submit to a pool) while holding one, and always acquire multiple locks in a **global order** to avoid deadlock.

## 3. Atomics and CAS — the lock-free core

An `AtomicInteger` wraps a **compare-and-set** (CAS) hardware instruction: "set to `new` only if it still equals `expected`." The universal idiom is the **CAS retry loop**:

```java
int n;
do { n = counter.get(); }
while (!counter.compareAndSet(n, n + 1));   // retry if someone raced us
```

This is how you enforce a **bound without a lock** — the crux of admission control in [[coding-patterns/keyed-serial-executor]]:

```java
while (true) {
    int n = inflight.get();
    if (n >= MAX) return false;                 // full → reject
    if (inflight.compareAndSet(n, n + 1)) break; // we won a slot
}
```

- **Why not `getAndIncrement` then check?** Because then you've already incremented past the bound and have to walk it back — a check-then-act race. CAS makes "check the bound AND claim the slot" a single atomic decision.
- **ABA problem** — value goes A→B→A and CAS wrongly succeeds; fix with `AtomicStampedReference` (version tag).
- **`LongAdder`/`LongAccumulator`** — under heavy contention, striped counters that beat a single hot `AtomicLong` (trade exact-instant reads for throughput). Use for high-rate metrics.

## 4. Semaphore — counting permits

A `Semaphore(n)` hands out `n` permits; `acquire()` blocks when none are left, `release()` returns one. It's the direct expression of "**at most N concurrently**." Two ways to bound concurrency to 4:
- `Semaphore(4)` guarding an unbounded/cached pool — decouples the permit count from thread count.
- A `newFixedThreadPool(4)` — the pool *is* the semaphore; simplest when the permit and the worker are the same thing.

A `Semaphore(1)` is a non-reentrant mutex (and, unlike `synchronized`, can be released by a different thread — useful, dangerous).

## 5. Coordination and signaling

- **`wait`/`notifyAll`** (with a monitor) — the low-level primitive. **Always wait in a `while` loop** re-checking the condition (guards spurious wakeups and lost-wakeup races). Prefer `notifyAll` unless you can prove one waiter suffices.
- **`Condition`** (on a `ReentrantLock`) — the same, with multiple named wait-sets (e.g. `notFull`/`notEmpty` in a bounded buffer).
- **`CountDownLatch`** — one-shot gate: N `countDown()`s release all `await()`ers. "Wait for K things to finish once." Used for a shutdown-drain: wait until the in-flight count hits 0.
- **`CyclicBarrier`** — N threads rendezvous, then all proceed; **reusable** across rounds (phased simulations).
- **`Phaser`** — dynamic-party barrier; parties register/deregister across phases.
- **`BlockingQueue`** (`ArrayBlockingQueue`, `LinkedBlockingQueue`) — the producer/consumer backbone: `put` blocks when full, `take` blocks when empty. Backpressure and hand-off in one object; it *is* the queue inside a thread pool.
- **`Future` / `CompletableFuture`** — a handle to an async result; `CompletableFuture` composes (`thenApply`, `thenCompose`, `allOf`) so you can chain work without manual latches. Per-key serialization can be expressed as chaining each new task onto the key's current `CompletableFuture`.

## 6. Concurrent collections — don't lock a plain HashMap

- **`ConcurrentHashMap`** — lock-striped; concurrent reads, bounded-contention writes. The key skill is its **atomic compound ops**: `computeIfAbsent`, `compute`, `merge` run the lambda **atomically per bucket**, which lets you "get-or-create a per-key queue" without a race. (Caveat: the lambda must be short and must not touch the same map.)
- **`ConcurrentLinkedQueue`** — lock-free unbounded FIFO; no blocking (`poll` returns null when empty).
- **`CopyOnWriteArrayList`** — snapshot-on-write; read-mostly listener lists. O(n) writes — never for write-heavy data.

## Pitfalls (where concurrency bugs actually come from)

- **Check-then-act** — `if (!map.containsKey(k)) map.put(k, v)` is a race; use `putIfAbsent`/`computeIfAbsent`. Same shape as the admission bug above.
- **Lost wakeup / no loop** — `if (empty) wait();` instead of `while` → spurious/lost wakeup bug.
- **Deadlock** — inconsistent lock ordering, or blocking while holding a lock. **Don't submit to a pool or call `run()` while holding a lock.**
- **Missing the release/decrement** — every acquire/increment needs a `finally` that releases/decrements, or the resource leaks and the system wedges (the counter climbs to the cap and rejects forever).
- **Busy-polling** (`while(true){ scan(); sleep(100); }`) — burns CPU, adds latency, and often serializes dispatch. Prefer **completion-driven hand-off** (a finishing task schedules the next) or a `BlockingQueue`.
- **Swallowing exceptions in a worker** — one uncaught throwable kills the worker thread or stalls a serial chain; wrap task bodies in `try/catch` to isolate failures.

## How they compose (the KeyedTaskExecutor mapping)

Every requirement of [[coding-patterns/keyed-serial-executor]] is one construct:

| Requirement | Construct |
|---|---|
| ≤4 concurrent | `newFixedThreadPool(4)` (or `Semaphore(4)`) |
| ≤1000 in flight + reject-when-full + reject-after-shutdown | `AtomicInteger` CAS-admission loop + `volatile boolean` |
| Same key serial, in order | `ConcurrentHashMap.computeIfAbsent` → per-key `ArrayDeque` + a `scheduled` flag; re-schedule on completion |
| Failure isolation | `try/catch` around `task.run()` |
| Drain-with-timeout shutdown | decrement-to-zero **signal** + timed `wait`, then `shutdownNow()` |

## Key points
- Classify first: visibility, mutual exclusion, lock-free state, bounded resource, or coordination — then pick the cheapest tool that closes *that* race.
- `volatile` gives visibility/ordering, **not** atomicity of read-modify-write.
- The **CAS loop** is how you enforce a bound lock-free; check-and-claim in one atomic step, not increment-then-check.
- A **fixed thread pool is a semaphore** on concurrency; use `Semaphore` when threads and permits should differ.
- `ConcurrentHashMap.compute*` runs the lambda **atomically per key** — the clean way to get-or-create per-key state.
- Prefer **completion-driven hand-off / blocking queues** over busy-poll loops.
- Every acquire needs a `finally` release; every worker body needs a `try/catch`.

## Interview angle

> "I don't reach for a lock reflexively — I classify the race first. Visibility-only? `volatile`. A bounded counter? An `AtomicInteger` CAS loop, because check-then-act on a bound is itself a race and CAS makes 'check and claim' atomic. Bound concurrency to N? A fixed pool or a `Semaphore(N)` — the pool *is* the semaphore. Per-key state without a global lock? `ConcurrentHashMap.computeIfAbsent`, which runs the factory atomically per bucket. And I avoid the two classic smells: busy-poll dispatch loops — I prefer completion-driven hand-off — and a missing `finally` that leaks a permit so the system wedges at its cap."

## Connections
- [[coding-patterns/keyed-serial-executor]] — the worked problem these constructs compose into; each requirement maps to one tool
- [[theory/concurrency-primitives]] — the execution units (process/thread/green thread) these constructs schedule and synchronize
- [[theory/actor-model-message-passing]] — the alternative to shared-memory synchronization: no locks, serialize by owning state in one actor's mailbox
- [[system-design-concepts/dispatch-and-matching]] — the same atomic check-and-claim (CAS) idea at the distributed layer: "first-accept-wins behind a two-invariant claim"
- [[system-design-concepts/exactly-once-semantics]] — idempotency/dedup is the distributed cousin of failure-isolation + safe retries here

## Sources
- [[sources/docs/azure-storage-interview-loop]] — Part 2 (KeyedTaskExecutor); constructs extracted from the live coding exercise
</content>
