# CFS: The Completely Fair Scheduler

Linux's default scheduler from 2.6.23 (2007) to 6.5 (2023). CFS models an **ideal multitasking CPU** that runs every runnable task simultaneously at `1/n` speed, then approximates it on real hardware by always running the task that is *furthest behind* its fair share. "Furthest behind" is measured by a single per-task counter — **`vruntime`** — and the whole scheduler is essentially "pick the smallest `vruntime`, keep them equal." Its objective is [[theory/cpu-scheduler-goals|weighted fairness]]; everything below is mechanism.

## vruntime: the fairness clock

`vruntime` (virtual runtime, nanoseconds) is the **weight-normalized** CPU time a task has consumed. It advances as a task runs, at a rate inversely proportional to the task's weight:

```
delta_vruntime = delta_exec × (NICE_0_LOAD / weight)     NICE_0_LOAD = 1024
```

where `delta_exec` is the *actual* wall-clock nanoseconds the task just ran. The consequence is the entire trick:

- **nice 0** (weight 1024): `vruntime` advances at exactly wall-clock rate (`×1`).
- **High weight** (low nice, more important): `vruntime` advances *slower* → it stays "behind" → gets picked more → **more CPU**.
- **Low weight** (high nice): `vruntime` races *ahead* → gets picked less → **less CPU**.

Fairness = keeping all `vruntime`s equal. Priority = making the clock tick at different speeds. One counter does both.

### Worked example

Two always-runnable tasks: **A** at nice 0 (weight 1024), **B** at nice +5 (weight 335). After each runs for `delta` ns:

```
A.vruntime += delta × 1024/1024 = 1.00 × delta
B.vruntime += delta × 1024/335  = 3.06 × delta
```

B's clock runs ~3× faster, so CFS picks B one-third as often. Shares settle at `1024/(1024+335) ≈ 75%` for A, `25%` for B — matching the weight ratio.

## nice → weight

`nice` (−20…+19) is mapped to weight through a fixed table (`sched_prio_to_weight[]`), calibrated so **each nice level ≈ ±10% CPU** (adjacent weights differ by ~1.25×):

| nice | weight | | nice | weight |
|---|---|---|---|---|
| −20 | 88761 | | +1 | 820 |
| −10 | 9548 | | +5 | 335 |
| −5 | 3121 | | +10 | 110 |
| **0** | **1024** | | +19 | 15 |

Two tasks one nice level apart split the CPU ~55/45 (≈10% gap). The absolute values don't matter — only *ratios* do, because share = `weight / Σweights`.

## The red-black tree: how consumed vruntime is tracked

CFS keeps all runnable tasks in a **red-black tree keyed by `vruntime`** (per-CPU, one `cfs_rq` per core). An RB tree is a self-balancing BST, so it stays O(log n) deep.

```
                 [ vruntime=124 ]
                /               \
      [ vr=118 ]                 [ vr=131 ]
       /      \                       \
[vr=115]      [vr=120]                 [vr=140]
   ▲
   └── leftmost = smallest vruntime = "furthest behind" = runs next  (cached, O(1))
```

- **Selection** is "pick leftmost node." CFS caches a pointer to it (`rb_leftmost`), so picking the next task is **O(1)**; insert/remove are **O(log n)**.
- **The running task is not conceptually in the tree** — its `vruntime` is increasing while it runs, which would corrupt the ordering. It's dequeued while on-CPU.
- **How consumption is tracked:** on every scheduler tick, and at every enqueue/dequeue/preemption, `update_curr()` runs:
  1. compute `delta_exec` = now − `exec_start` (wall-clock since last update),
  2. add the weighted amount to `curr->vruntime`,
  3. advance `cfs_rq->min_vruntime`.
  When the running task is put back, it is **re-inserted at its new (larger) `vruntime`** — which naturally lands it further right. So the tree doesn't "update in place"; consumption is recorded by **remove → advance vruntime → re-insert**.

### min_vruntime: the monotonic floor

`min_vruntime` is a per-`cfs_rq` value that only ever moves forward, tracking roughly the smallest `vruntime` present. It solves two problems:

- **Normalizing newcomers.** A task that just forked or woke from sleep has a stale (tiny) `vruntime`. Inserting it as-is would let it hog the CPU for a long time to "catch up," starving everyone. So CFS sets a joining task's `vruntime` relative to `min_vruntime`:
  - **new task** (`place_entity`, START_DEBIT): `vruntime = min_vruntime + one slice` — so it can't exploit a zero start.
  - **woken sleeper**: `vruntime = max(vruntime, min_vruntime − sched_latency/2)` — a small credit for having slept (used nothing), which is CFS's *indirect* latency boost for interactive tasks.
- **Preventing overflow / drift.** Keys are stored as `vruntime − min_vruntime` offsets, keeping the comparisons bounded even as absolute `vruntime` grows for hours.

## Time slice and period

CFS doesn't use a fixed quantum. It targets a **scheduling period** (`sched_latency`, ~6 ms on desktop) in which *every* runnable task should run once, then hands each task a slice proportional to its weight:

```
slice_i = sched_period × weight_i / Σ weights
```

To avoid death-by-context-switch when `n` is large, a floor `sched_min_granularity` (~0.75 ms) applies; once `n > sched_latency/min_granularity` (~8 tasks) the period stretches to `n × min_granularity`. A task is preempted when its accumulated `vruntime` overtakes the next task's by more than its slice — this is the latency↔throughput knob from [[theory/cpu-scheduler-goals]] made concrete.

## cgroups: priority for *groups*, not just tasks

cgroups make fairness **hierarchical** — you can give a *group* of tasks (a container, a user, a service) a CPU share, then subdivide it, independent of how many threads each holds. Two distinct mechanisms:

**1. Proportional share (`cpu.shares` in v1 / `cpu.weight` in v2).** A `task_group` is itself a schedulable entity (`sched_entity`) with its own `weight` and its own child `cfs_rq`. The tree is nested: the top-level tree picks a *group*, whose `cfs_rq` has its own tree that picks a task. A group's share is applied *recursively*:

```
effective_share(task) = (group_weight / Σ sibling group_weights)
                      × (task_weight  / Σ tasks in group)
```

So two containers with equal `cpu.weight` split the CPU 50/50 **even if one runs 1 thread and the other runs 100** — the 100 threads share their container's half. This is the property plain `nice` can't give you: `nice` is per-thread, so 100 nice-0 threads would swamp 1 nice-0 thread.

**2. Bandwidth control (`cpu.cfs_quota_us`/`cfs_period_us` in v1, `cpu.max` in v2).** A *hard cap*: "at most `quota` ns of CPU per `period` ns." When a group exhausts its quota in a period it is **throttled** — dequeued until the next period refills it, even if cores are idle. Shares are *relative and work-conserving* (use idle CPU if available); quota is *absolute and non-work-conserving* (a ceiling). Both can apply at once: shares divide contended CPU, quota caps absolute usage.

This hierarchical group scheduling is the OS-level substrate that cluster schedulers sit on — k8s `cpu.requests`/`limits` map directly onto shares/quota (see [[system-design-concepts/preemption-economics]]).

## Limitations that motivated EEVDF

CFS is fair, but fairness is *all* it directly expresses — and that's the problem:

- **`nice` conflates share with latency.** The only way to make a task run *sooner* is to raise its priority, which also gives it *more CPU*. There's no way to say "I want a *small* share but run me *promptly* when I wake" — e.g. a latency-sensitive audio thread that uses little CPU. Latency and throughput-share are welded together.
- **Latency was handled by accreted heuristics.** `GENTLE_FAIR_SLEEPERS`, wakeup-preemption granularity, `START_DEBIT`, sleeper credits — a pile of tunables approximating "be nice to interactive tasks," hard to reason about and easy to get wrong.
- **No principled deadline.** CFS has no notion of "this task must run within X ms." It only knows who's furthest behind.
- **Slice granularity is coarse and global.** `sched_min_granularity` is one knob for the whole system, not a per-task property.

EEVDF fixes exactly this by adding **lag** (a rigorous fairness measure) and a **requestable virtual deadline** that decouples latency from share — see [[theory/eevdf-scheduler]].

## Key points
- `vruntime` = weight-normalized CPU consumed; `delta_vruntime = delta_exec × 1024/weight`. Fairness = equalize it; priority = make it tick at different speeds.
- Higher weight (lower nice) → slower `vruntime` → picked more → more CPU. nice 0 = weight 1024; ~10% CPU per nice level (~1.25× per step).
- Runnable tasks live in a **red-black tree keyed by `vruntime`**; leftmost (smallest) runs next, cached O(1); insert/remove O(log n).
- Consumption is tracked by `update_curr()` (remove → advance `vruntime` → re-insert), not in-place edits. `min_vruntime` is a monotonic floor that normalizes new/woken tasks and bounds key growth.
- cgroups add **hierarchical** fairness: `cpu.shares`/`weight` = proportional (work-conserving), `cpu.max`/quota = hard cap (throttling). Groups are `sched_entity`s with nested `cfs_rq`s.
- CFS's flaw: `nice` welds latency to share, so interactive-but-cheap tasks can't ask for promptness without also taking CPU — the gap EEVDF closes.

## Interview angle

> "CFS keeps one number per task, `vruntime`, which is CPU time consumed divided by weight. It always runs the task with the smallest `vruntime` — the one furthest behind its fair share — so fairness is just 'keep all the vruntimes equal,' and priority is making the clock tick at different rates: a high-weight task's vruntime advances slower, so it stays behind and gets more CPU. Runnable tasks sit in a red-black tree keyed by vruntime; the leftmost node is next and is cached for O(1) pick, with O(log n) updates. The running task is pulled out of the tree, and on each tick `update_curr` advances its vruntime and reinserts it. `min_vruntime` is a monotonic floor that stops a freshly-woken task with a tiny vruntime from hogging the CPU to catch up. cgroups make this hierarchical — a container gets a weight and its threads share that slice. The reason Linux moved off CFS is that `nice` couples latency to share: you can't ask for low latency without also asking for more CPU. EEVDF splits those apart."

## Connections
- [[theory/cpu-scheduler-goals]] — CFS is the "fairness wins" point in the goal-tradeoff space; this page is the mechanism
- [[theory/eevdf-scheduler]] — the successor; keeps vruntime but adds lag + a requestable deadline to fix CFS's latency/share coupling
- [[system-design-concepts/preemption-economics]] — CFS + cgroup shares/quota are the OS-layer time-slicing that cluster schedulers (k8s requests/limits) sit on top of
- [[theory/concurrency-primitives]] — what a context switch actually costs, i.e. the price CFS pays each time it preempts on vruntime
- [[theory/copy-on-write-vs-mvcc]] — an unrelated use of the same red-black/versioning toolkit; CFS's RB tree is the ordered-structure counterpart

## Sources
- [[sources/docs/linux-cpu-scheduling]] — deep-dive extract: vruntime, RB-tree bookkeeping, nice→weight, cgroup group scheduling + bandwidth, and the CFS→EEVDF motivation
- [Linux kernel docs: `Documentation/scheduler/sched-design-CFS.rst`](https://docs.kernel.org/scheduler/sched-design-CFS.html) — canonical CFS reference
- [CFS bandwidth control (`sched-bwc.rst`)](https://docs.kernel.org/scheduler/sched-bwc.html) — quota/period throttling
- [Operating Systems: Three Easy Pieces — Proportional Share](https://pages.cs.wisc.edu/~remzi/OSTEP/cpu-sched-lottery.pdf) — the fair-share lineage CFS descends from
