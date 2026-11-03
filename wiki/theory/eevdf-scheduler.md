# EEVDF: Earliest Eligible Virtual Deadline First

Linux's default scheduler since 6.6 (late 2023), replacing [[theory/cfs-completely-fair-scheduler|CFS]]. EEVDF (from a 1995 paper by Stoica & Abdel-Wahab) keeps CFS's weighted-fairness foundation but fixes CFS's core defect: that `nice` welds *latency* to *share*. It does so with two additions — a rigorous fairness measure called **lag**, and a per-task **virtual deadline** derived from a *requestable* time slice. The selection rule is in the name: among all **eligible** tasks, run the one with the **earliest virtual deadline**.

Read [[theory/cpu-scheduler-goals]] first — EEVDF's whole reason to exist is promoting *latency* to a first-class, requestable goal alongside fairness.

## The system virtual time V

EEVDF tracks a global **virtual time** `V` per run queue — the weighted-average `vruntime` of all runnable tasks (`avg_vruntime` in the kernel):

```
V = Σ(weight_i × vruntime_i) / Σ(weight_i)
```

`V` is the "fair clock": where every task's `vruntime` *would* be if the CPU had been shared perfectly up to now. It's the reference point both new concepts are measured against. (CFS's `min_vruntime` was a *floor*; EEVDF's `V` is a weighted *mean* — a stronger, more precise anchor.)

## Lag: the exact fairness measure

Each task has a **lag** — how far behind (or ahead of) its fair share it is, in virtual time:

```
lag_i = V − vruntime_i
```

- **`lag > 0`** → the task has received **less** than its fair share (it's *behind*, owed CPU).
- **`lag < 0`** → the task has received **more** than fair (it's *ahead*, overspent).
- **`lag = 0`** → exactly fair.

Lag is the rigorous version of CFS's fuzzy "furthest behind." The scheduler's fairness invariant is to drive lag toward zero, and crucially **lag is preserved across sleep** — a task that goes to sleep keeps its lag and has it restored (decayed) on wakeup, so sleeping can't be used to game the fair share (the problem CFS patched with sleeper-credit heuristics).

## Eligibility: the "EE" in EEVDF

A task is **eligible** only when its `vruntime` has caught up to the virtual time — i.e. it is *not ahead* of its fair share:

```
eligible  ⟺  vruntime_i ≤ V   ⟺   lag_i ≥ 0
```

This is the concept CFS entirely lacked. A task that just consumed a big slice goes **lag < 0 (ineligible)** and is *excluded from selection* until `V` advances enough to catch up to it — even if its deadline is near. Eligibility is what enforces long-term fairness and stops a low-latency task from starving others by requesting tiny slices forever: each time it runs it goes ineligible and must wait its turn. The **eligible-time** `ve_i` is the virtual time at which a task becomes eligible again.

## Virtual deadline: the "VD" — where latency enters

Each task **requests a time slice** `r` (the amount of CPU it wants per activation, e.g. 3 ms). EEVDF converts that request into a **virtual deadline**:

```
virtual_deadline_i = eligible_time_i + (r_i × NICE_0_LOAD / weight_i)
```

i.e. eligible time plus the request expressed in virtual time. Then: **among eligible tasks, pick the earliest virtual deadline.**

The elegant part is what the slice request now controls:

- **Small requested slice → nearer deadline → gets picked first → low latency.** A task can ask to run *promptly* by requesting a *short* slice.
- It does **not** get more CPU by doing so — a short slice means it runs *sooner* but *briefly*, then goes ineligible. Over time its share is still governed by its weight (via lag/eligibility).

**This is the decoupling CFS couldn't do:** latency is set by the *slice request*, share is set by the *weight*. An audio thread asks for a 1 ms slice → tight deadline → runs almost immediately on wakeup → but still only consumes its small fair share. Under CFS the only lever was `nice`, which would have handed it more CPU too.

### latency-nice / sched_attr

Userspace sets the request via `sched_setattr()` — `sched_runtime` (the slice) and the **`latency-nice`** attribute (−20…+19). Lower `latency-nice` → shorter default request → tighter deadlines → more responsive, *without* changing the task's CPU share (still set by ordinary `nice`/weight). Two orthogonal knobs at last.

## The augmented red-black tree

EEVDF still keeps runnable tasks in a **red-black tree keyed by `vruntime`** (so eligibility — a `vruntime ≤ V` test — is a left-side range), but *augments* each node with the **minimum virtual deadline of its subtree** (`min_deadline`):

```
node: { vruntime (key), deadline, min_deadline = min(own deadline, children's min_deadline) }
```

Selection = "leftmost eligible task with earliest deadline," which the augmentation answers in **O(log n)**: walk the tree pruning by eligibility (`vruntime ≤ V`) while using each subtree's `min_deadline` to find the earliest deadline among the eligible set. Insert/update stay O(log n) (the augmented field is recomputed on rotation). So EEVDF matches CFS's asymptotic cost while enforcing a richer rule — the **low-overhead** goal from [[theory/cpu-scheduler-goals]] is preserved.

## How a running task updates its state

Mechanically similar to CFS's `update_curr()` — as a task runs, its `vruntime` advances at `delta_exec × 1024/weight` — but with an EEVDF twist:

1. running consumes the requested slice; `vruntime` climbs toward and past `V`.
2. once it has run its slice (or `vruntime` pushes `lag ≤ 0`), the task becomes **ineligible**, is removed, and re-inserted with a **new eligible-time and a fresh deadline** for its next activation.
3. `V` (the weighted average) is recomputed as tasks enqueue/dequeue.

So, like CFS, "consumption" is recorded by *remove → advance vruntime → recompute deadline → re-insert* — never in-place — but the re-insertion now also stamps a new deadline, and the pick is deadline-ordered rather than vruntime-ordered.

## CFS → EEVDF at a glance

| | **CFS** | **EEVDF** |
|---|---|---|
| Fairness anchor | `min_vruntime` (monotonic floor) | `V` = `avg_vruntime` (weighted mean) |
| Fairness measure | implicit "smallest vruntime" | explicit **lag** = `V − vruntime` |
| Selection rule | least `vruntime` | earliest **deadline** among **eligible** (`lag ≥ 0`) |
| Latency control | `nice` (couples share + latency) + heuristics | **requested slice** / `latency-nice` (decoupled from share) |
| Slice | derived from `sched_latency`, global | **per-task requestable** `r` |
| Tree | RB keyed by vruntime, cache leftmost | RB keyed by vruntime, **augmented with `min_deadline`** |
| Sleeper handling | sleeper-credit heuristics | **lag preserved** across sleep (principled) |

## Key points
- EEVDF keeps weighted fairness (vruntime) but adds **lag**, **eligibility**, and a **virtual deadline** — replacing CFS's heuristic latency handling with a principled model.
- **`V` = avg_vruntime** (weighted mean) is the fair clock; **lag = V − vruntime** measures exactly how far from fair a task is.
- **Eligible ⟺ vruntime ≤ V (lag ≥ 0):** a task that overspent is excluded until it catches up — this enforces long-term fairness and prevents tiny-slice gaming.
- **Deadline = eligible_time + r×1024/weight;** pick earliest deadline among eligible. A **small requested slice → nearer deadline → lower latency, without more CPU**.
- That decouples latency (slice request / `latency-nice`) from share (weight/`nice`) — the exact thing CFS's `nice` could not separate.
- Implemented with an **augmented RB tree** carrying subtree `min_deadline`, so selection stays **O(log n)**.

## Interview angle

> "EEVDF keeps CFS's vruntime but fixes that `nice` couples latency and share. It defines a system virtual time V — the weighted-average vruntime, the fair clock — and per task a lag = V − vruntime, so positive lag means owed CPU, negative means overspent. A task is *eligible* only when vruntime ≤ V, i.e. it isn't ahead; overspenders are excluded until they catch up, which is what guarantees fairness. Each task also requests a slice, turned into a virtual deadline = eligible-time + slice/weight, and the scheduler runs the earliest deadline among eligible tasks. The magic is that a small requested slice gives a nearer deadline, so a latency-sensitive task runs *promptly* on wakeup but doesn't get *more* CPU — latency comes from the slice, share from the weight, finally decoupled. It's all kept O(log n) with a red-black tree augmented with each subtree's minimum deadline. Lag is preserved across sleep, so it doesn't need CFS's pile of sleeper-credit heuristics."

## Connections
- [[theory/cfs-completely-fair-scheduler]] — the predecessor EEVDF extends; vruntime/weight/cgroup machinery carries over unchanged
- [[theory/cpu-scheduler-goals]] — EEVDF is CFS + latency promoted to a first-class *requestable* goal; this is that tradeoff resolved
- [[system-design-concepts/preemption-economics]] — EEVDF is the current OS-layer executor beneath cluster schedulers; cgroup weights/quota still apply
- [[theory/concurrency-primitives]] — a short requested slice means more frequent preemption, so the context-switch cost is the price of the latency EEVDF grants

## Sources
- [[sources/docs/linux-cpu-scheduling]] — deep-dive extract: V/lag/eligibility/deadline, the augmented RB tree, and the CFS→EEVDF motivation
- [LWN: An EEVDF CPU scheduler for Linux](https://lwn.net/Articles/925371/) — the introductory writeup
- [LWN: Completing the EEVDF scheduler](https://lwn.net/Articles/969062/) — follow-on (latency-nice, slice requests)
- Stoica & Abdel-Wahab, *Earliest Eligible Virtual Deadline First: A Flexible and Accurate Mechanism for Proportional Share Resource Allocation* (TR-95-22, 1995) — the original algorithm (search the title; PDF widely mirrored)
- [Linux kernel docs: `Documentation/scheduler/sched-eevdf.rst`](https://docs.kernel.org/scheduler/sched-eevdf.html) — canonical reference
