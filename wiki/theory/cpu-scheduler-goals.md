# CPU Scheduler Goals: The Objectives That Trade Off

A CPU scheduler multiplexes more runnable threads than there are cores. *How* it picks the next thread is downstream of *what it is optimizing for* — and the classic goals are mutually contradictory, so every scheduler is a chosen point in a tradeoff space, not a "best" algorithm. Naming the goals first is what makes CFS and EEVDF legible: each is a different answer to "which goal wins when they conflict?"

## The goals

| Goal | Definition | Metric | Who wants it |
|---|---|---|---|
| **Responsiveness / latency** | Time from *becoming runnable* to *actually running* | wakeup latency, p99 scheduling delay | Interactive UI, audio, a waking network thread |
| **Throughput** | Useful work completed per unit time | tasks/sec, jobs/hour | Batch, compilation, data processing |
| **CPU utilization** | Fraction of cycles doing user work (not idle, not scheduler overhead) | % busy | Everyone; the datacenter bill |
| **Fairness** | Each task gets its entitled share of CPU | max–min share deviation | Multi-tenant boxes, `nice`-weighted workloads |
| **Turnaround** | Total time from submission to completion | wall-clock per job | Batch jobs (a human is *not* waiting per-slice) |
| **Deadline adherence** | Work finishes before a fixed time | % deadlines met | Real-time: media, control loops |
| **Low overhead** | Cycles spent *deciding* + context-switching stay small | scheduler CPU %, switch cost | High core counts, high context-switch rates |
| **Starvation-freedom** | Every runnable task eventually runs | max wait time bound | A correctness floor under all of the above |

## Why they fight

The tensions are the whole point — you cannot maximize all at once:

- **Responsiveness vs throughput.** Preempting a CPU-bound task the instant an interactive one wakes cuts latency but adds a context switch (cache/TLB cold-start after — see [[theory/concurrency-primitives]]). More switches = lower throughput. A longer time slice does the reverse. This is the **round-robin (response time) vs SJF/FIFO (turnaround)** tradeoff.
- **Fairness vs responsiveness.** *Strict* fairness says a task that just used a lot of CPU must wait for others to catch up — exactly when an interactive task that was *sleeping* (using nothing) wants to run *now*. Pure fairness starves latency. **This is precisely the gap that pushed Linux from CFS to EEVDF.**
- **Utilization vs latency.** Packing a core to 100% busy maximizes utilization but builds a run queue, so wakeup latency climbs. Headroom is latency insurance you pay for in idle cycles.
- **Overhead vs everything.** The scheduler competes with the work it schedules. At datacenter scale, scheduling + related low-level building blocks is a measurable "tax" (~5% of CPU; Kanev et al., ISCA 2015) — so a "smarter" but heavier algorithm can lose net.

## The batch vs interactive split

The single biggest lever is **what the workload is waiting on**:

- **Interactive** → a *human* waits on each response → optimize **response time** → **time-slice / preempt** so no task waits long.
- **Batch** → nobody waits per-slice, only for the final result → optimize **turnaround + throughput** → **run to completion + queue**, minimize switches.

A general-purpose OS scheduler (CFS, EEVDF) must serve *both* on the same box, which is why it leans on fairness as the neutral baseline and then bolts on latency handling. This same objective-split reappears one layer up — cluster schedulers optimize turnaround and thus drift back toward FIFO/queueing (see [[system-design-concepts/preemption-economics]]).

## Where the two Linux schedulers land

| | **CFS** (2007–2023) | **EEVDF** (6.6, 2023–) |
|---|---|---|
| Primary objective | **Fairness** (equal weighted CPU share) | Fairness **+ bounded latency** as a first-class, requestable property |
| Latency handling | Indirect — heuristics, small slices, `nice` | Explicit — per-task *requested slice* → virtual **deadline** |
| Selection rule | Least `vruntime` | Earliest **virtual deadline** among **eligible** tasks |

Both keep **starvation-freedom** and **low overhead** (O(log n) via a red-black tree) as non-negotiable floors; they differ in how they trade **responsiveness against fairness**. That's the throughline into the two detail pages.

## Key points
- A scheduler is a *chosen tradeoff point*, not a universally optimal algorithm — start any scheduler discussion by naming the goal it prioritizes.
- The core conflicts: responsiveness↔throughput, fairness↔responsiveness, utilization↔latency, and overhead↔all.
- Preemption improves latency but costs a context switch (throughput + cache/TLB cost), which is the RR-vs-SJF tradeoff in one sentence.
- Batch optimizes turnaround (→ queue, run-to-completion); interactive optimizes response time (→ time-slice). A general OS must do both.
- CFS chose fairness; EEVDF keeps fairness but makes latency an explicit, requestable dimension — the reason for the 6.6 switch.

## Interview angle

> "Before naming an algorithm I'd name the goals, because they conflict: responsiveness, throughput, utilization, fairness, turnaround, deadlines, low overhead, and starvation-freedom. You can't max all of them — preempting for latency costs context switches that hurt throughput; strict fairness makes a just-woken interactive task wait behind CPU hogs. Batch workloads optimize turnaround so they queue and run to completion; interactive ones optimize response time so they time-slice. A general-purpose OS scheduler has to serve both, so it uses fairness as the neutral baseline. CFS optimized *purely* for weighted fairness and treated latency with heuristics; EEVDF keeps the fairness but adds latency as a first-class, per-task requestable property — which is exactly why Linux switched in 6.6."

## Connections
- [[theory/cfs-completely-fair-scheduler]] — the scheduler that chose weighted fairness as its objective; vruntime is the mechanism
- [[theory/eevdf-scheduler]] — keeps fairness but promotes latency to a requestable first-class goal
- [[system-design-concepts/preemption-economics]] — the same goal-conflict one layer up: cluster schedulers optimize turnaround, so they queue/kill instead of time-slicing
- [[theory/concurrency-primitives]] — the context-switch cost that makes "preempt for responsiveness" a real, quantifiable tradeoff against throughput

## Sources
- [[sources/docs/linux-cpu-scheduling]] — CFS/EEVDF deep-dive: goals, vruntime, red-black tree, cgroup weights, and the EEVDF lag/eligibility/deadline model
- [Operating Systems: Three Easy Pieces — Scheduling](https://pages.cs.wisc.edu/~remzi/OSTEP/) — chapters 7–9 (metrics, MLFQ, proportional-share/lottery)
