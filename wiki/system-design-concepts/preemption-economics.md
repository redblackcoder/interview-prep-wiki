# Preemption Economics

Why distributed schedulers (Spark, YARN, k8s, EMR) **queue, scale, or kill** instead of context-switching the way an OS does. The same scheduling theory produces opposite-looking designs at different layers because the *cost of preemption* inverts as you go up the stack.

The governing principle:

> **Preempt aggressively when `cost(save + restore state) ≪ quantum of useful work`. Otherwise don't preempt — queue, or kill-and-redo.**

## Two scheduling layers, not one

OS scheduling does **not** disappear in a cluster — it still runs underneath. There are two stacked schedulers answering different questions:

| Layer | Question | Mechanism |
|---|---|---|
| **Placement / admission** (YARN, k8s, Spark) | *Where* does this work go, and *when* may it start at all? | queue, scale, kill — **no time-slicing** |
| **Execution** (OS: CFS + cgroups) | Given work is on this box, who's on the CPU this millisecond? | preempt-and-resume context switch |

When a Spark executor runs on a node, its threads are still context-switched by CFS and throttled by cgroups (`cpu.shares` / `cfs_quota`). The distributed layer sits *on top*.

## Why the context switch doesn't survive the trip up

The context switch is the OS's "ultimate tool" *because* the state to preserve is tiny and memory stays resident. That stops being true for a distributed unit of work.

| | OS thread | Spark task / container / pod |
|---|---|---|
| State to preserve | registers + stack: ~KB; memory stays in RAM | sort buffers / hash aggregations / shuffle blocks / cached partitions: **MB–GB** |
| Save/restore cost | ~1–5 µs (register swap) | seconds–minutes (checkpoint to disk/network); often impossible without app cooperation |
| Useful quantum | ~ms (CFS `sched_latency` ≈ 6 ms) | seconds–minutes |
| Overhead ratio | µs / ms → <1% → **time-slice freely** | seconds / seconds → **catastrophic** → don't |

A Spark task's "register file" is its multi-GB working set. There is no cheap snapshot-and-resume, so the ultimate tool has to change.

## What replaces preempt-and-resume

1. **Admission control / queueing** — don't start work you can't run to completion. An at-capacity EMR cluster makes new jobs *wait*, because starting them would either force killing in-flight work (wasted) or time-slice everyone slower and risk memory blowup. Batch optimizes **turnaround**, not response time, so it drifts back toward FIFO / run-to-completion + queueing. (Interactive OSes optimize *response time* — a human is waiting — so they time-slice. Same RR-vs-SJF tradeoff from OS theory, different objective.)
2. **Elastic resource allocation** — instead of time-slicing *fixed* resources harder, change *how many* resources each job holds. Spark **dynamic allocation** adds/removes executors based on pending-task backlog; EMR managed scaling adds nodes. This is the flip of the OS assumption: the OS treats resources as fixed and work as elastic-in-time; the cluster treats work as lumpy and resources as elastic. Growing the cluster removes most of the *need* to time-slice.
3. **Kill-and-recompute (preemption without resume)** — YARN Capacity/Fair schedulers and k8s priority/preemption *do* preempt, but "preempt" means **kill the pod/container**, not pause it. The work is lost and must be recomputed. Because killing wastes work, it is used *sparingly* — only to enforce fairness, priority, or queue-shares — never to interleave for time-sharing.
4. **App-level checkpointing** — the only route to *resumable* preemption: the application cooperates by writing a snapshot (Spark `checkpoint()`, structured-streaming checkpoints). Transparent OS-style checkpoint/restore (CRIU) exists but is rarely used here. The cost just moved into the app.

**The crisp distinction: the OS preempts and _resumes_; the cluster preempts and _redoes_.**

## Fairness survives, generalized

- **Spark FAIR scheduler mode** round-robins *whole tasks* across pools so a big job doesn't starve a small one — but at the granularity of tasks that **run to completion**, not preemptive slices.
- **Dominant Resource Fairness (DRF)** (Fair Scheduler / Mesos) generalizes fairness from a scalar (CPU time, as in CFS) to a **vector** — CPU *and* memory *and* other resources at once.

## Distributed-only, with no real OS analog

- **Data locality / delay scheduling** (Zaharia et al.): Spark deliberately *waits* a few seconds for a slot near the data (`PROCESS_LOCAL → NODE_LOCAL → RACK_LOCAL → ANY`) rather than run immediately far from it. The faint analog is cache/NUMA affinity, but the cost ratio is wildly different — a cache miss is nanoseconds; shipping a GB partition across the network is seconds. Locality dominates distributed scheduling as it never does in CFS.
- **Speculative execution**: launch a *duplicate* of a straggler task, take whichever finishes first. No OS analog — single-machine threads don't randomly run 10× slow because of one bad disk or a hot node.
- **Gang / all-or-nothing scheduling**: a Spark stage often needs *N* slots *simultaneously* (a shuffle can't half-start). The OS never needs "schedule these 200 threads at once or none."

## Interview angle

> "OS scheduling time-slices *fixed* resources among *elastic, cheap-to-preserve* work; cluster scheduling allocates *elastic* resources among *lumpy, expensive-to-preserve* work. Preemption is only worth it when saving and restoring state is cheap relative to the work done between preemptions — true for a KB register file, catastrophic for a multi-GB shuffle. So the OS's preempt-and-resume context switch is replaced by queueing, elastic scaling, and kill-and-recompute. CPU is the one exception: it's compressible, so cgroups/CFS still time-slice it underneath."

## Connections
- [[system-design-concepts/compressible-vs-incompressible-resources]] — the CPU exception: the one resource the cluster still time-slices, and why memory can't be
- [[system-design-concepts/work-distribution]] — queueing, backpressure, and starvation-avoidance are the admission layer's tools; this page explains *when* work is allowed to start at all
- Builds on OS scheduling theory — CFS, the context switch, and the round-robin (response time) vs SJF/FIFO (turnaround) tradeoff
- [[theory/concurrency-primitives]] — the context-switch *cost* (TLB flush vs mode switch vs user-space) is exactly what makes preemption cheap or expensive at each layer

## Sources
- [[sources/docs/os-scheduling-to-distributed-scheduling]] — extends the OSTEP CFS chapter (incl. the Kanev et al. datacenter-tax finding) into Spark/YARN/k8s/EMR scheduling
