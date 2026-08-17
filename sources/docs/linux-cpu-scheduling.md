---
source: docs/linux-cpu-scheduling.md
source_url: https://github.com/redblackcoder/interview-prep-raw/blob/main/docs/linux-cpu-scheduling.md
type: doc
date_extracted: 2026-08-16
topic: theory
---

# Linux CPU Scheduling: Goals → CFS → EEVDF

## Key Ideas
- **A scheduler is a chosen point in a tradeoff space, not an optimum.** The goals — responsiveness/latency, throughput, utilization, fairness, turnaround, deadline adherence, low overhead, starvation-freedom — actively conflict. Preempting for latency costs a context switch (throughput + cache/TLB cold-start); strict fairness makes a just-woken interactive task wait behind CPU hogs. Batch optimizes turnaround → queue/run-to-completion; interactive optimizes response time → time-slice. A general-purpose OS scheduler must serve both, so it uses fairness as the neutral baseline.
- **CFS (2.6.23, 2007 → 6.5, 2023) = weighted fairness via `vruntime`.** `vruntime` is weight-normalized CPU consumed: `delta_vruntime = delta_exec × (NICE_0_LOAD / weight)`, `NICE_0_LOAD = 1024`. Equalizing vruntime = fairness; making the clock tick at different rates (via weight) = priority. Higher weight → slower vruntime → picked more → more CPU. One counter does both fairness and priority.
- **nice → weight** through `sched_prio_to_weight[]`, calibrated to ~10% CPU per nice level (~1.25× per step); nice 0 = 1024. Only weight *ratios* matter: share = `weight / Σweights`.
- **Red-black tree keyed by vruntime** holds runnable tasks (per-CPU `cfs_rq`). Leftmost = smallest vruntime = "furthest behind" = next; cached for O(1) pick, O(log n) insert/remove. The running task is dequeued (its vruntime is climbing). Consumption is recorded by `update_curr()` → **remove → advance vruntime → re-insert**, never in-place.
- **`min_vruntime`** is a per-rq monotonic floor: normalizes new tasks (`min_vruntime + slice`, START_DEBIT) and woken sleepers (`max(vruntime, min_vruntime − sched_latency/2)`, a small interactivity credit), and bounds key growth (keys stored as `vruntime − min_vruntime`). Stops a stale tiny-vruntime task from hogging CPU to catch up.
- **CFS slice** = `sched_period × weight/Σweights`, with `sched_min_granularity` (~0.75 ms) floor; period = `sched_latency` (~6 ms) until n>~8, then stretches to `n × min_granularity`.
- **cgroups = hierarchical fairness for groups, not just threads.** (1) `cpu.shares` (v1) / `cpu.weight` (v2): a `task_group` is a `sched_entity` with a nested `cfs_rq`; effective share = group-share × in-group task-share, applied recursively. Two equal-weight containers split 50/50 regardless of thread counts — which plain per-thread `nice` cannot do. (2) `cpu.cfs_quota_us`/`period` (v1) / `cpu.max` (v2): hard cap → **throttling** (dequeue till period refill) even if cores idle. Shares = relative + work-conserving; quota = absolute + non-work-conserving. k8s `requests`/`limits` map onto shares/quota.
- **Why CFS was replaced:** `nice` welds latency to share — the only way to run *sooner* is to raise priority, which also grants *more CPU*. No way to say "small share but prompt on wakeup" (e.g. audio). Latency was handled by accreted heuristics (GENTLE_FAIR_SLEEPERS, sleeper credit, START_DEBIT); no principled deadline.
- **EEVDF (6.6, late 2023 →)** keeps vruntime but adds three concepts: **(a) V = avg_vruntime**, the weighted-average vruntime (fair clock; stronger than CFS's floor). **(b) lag = V − vruntime**: exact fairness measure — >0 owed CPU, <0 overspent; preserved across sleep (no sleeper heuristics needed). **(c) eligibility**: eligible ⟺ vruntime ≤ V ⟺ lag ≥ 0 — overspenders are excluded from selection until they catch up, enforcing long-term fairness and blocking tiny-slice gaming.
- **EEVDF selection = earliest virtual deadline among eligible.** `deadline = eligible_time + r × 1024/weight`, where `r` is a *requestable* slice. Small requested slice → nearer deadline → picked sooner → **low latency, but NOT more CPU** (it then goes ineligible; share still governed by weight). This **decouples latency (slice request / `latency-nice` via `sched_setattr`) from share (weight/`nice`)** — the exact thing CFS couldn't separate.
- **Augmented RB tree:** still keyed by vruntime (eligibility = a left-side `vruntime ≤ V` range), each node augmented with subtree `min_deadline`, so "leftmost eligible with earliest deadline" is O(log n). Matches CFS's asymptotic cost with a richer rule.

## My Understanding
- The clean mental model: **CFS = "smallest weight-normalized clock wins"; EEVDF = "among those not ahead of fair, earliest deadline wins."** Both track consumed time as vruntime in a red-black tree; EEVDF layers a second ordering (deadline) and an admission gate (eligibility) on top.
- The single sentence that explains the 6.6 switch: **CFS's only latency knob (`nice`) also changes CPU share; EEVDF's slice request changes latency *without* changing share.** Lag + eligibility are what let it grant promptness without letting a task cheat its long-run share.
- vruntime bookkeeping is the same shape in both — a running task isn't edited in place, it's removed, its vruntime advanced by `delta_exec × 1024/weight`, then reinserted (EEVDF additionally stamps a fresh deadline). "How does the RB tree track consumed vruntime" = `update_curr()` on every tick/enqueue/dequeue + the remove/re-insert.
- cgroups are the bridge to [[system-design-concepts/preemption-economics]]: hierarchical shares/quota are the OS-layer time-slicing that k8s requests/limits declare as policy. CPU stays time-sliceable (compressible) at both layers; this is the "CPU exception."

## Open Questions
- Exact decay applied to preserved lag on wakeup — how much of a sleeper's lag survives, and over what horizon?
- How does EEVDF interact with SMP load-balancing / migration — is V per-rq only, and how is cross-CPU fairness reconciled when a task migrates (its vruntime is renormalized against the new rq's V)?
- Real measured impact of `latency-nice` on tail latency for audio/gaming vs the old CFS heuristics — where's the win biggest?
- Where does `sched_ext` (BPF pluggable schedulers, 6.12) fit relative to EEVDF — replacement path or experimentation sidecar?

## Connections
- Seeds: [[theory/cpu-scheduler-goals]] — the goal-tradeoff hub page
- Seeds: [[theory/cfs-completely-fair-scheduler]] — vruntime, RB tree, nice→weight, cgroups
- Seeds: [[theory/eevdf-scheduler]] — V/lag/eligibility/deadline, augmented tree
- Relates to: [[system-design-concepts/preemption-economics]] — CFS/cgroups are the OS executor layer beneath cluster schedulers; extends this "downward"
- Relates to: [[theory/concurrency-primitives]] — context-switch cost is what preemption spends; the RR-vs-SJF tradeoff in goals
