# OS Scheduling → Distributed Data-Processing Scheduling (discussion capture)

Raw notes captured while reading the OSTEP scheduling chapter (Completely Fair
Scheduler, CFS). Starts from a passage about scheduler overhead, then extends OS
scheduling theory to distributed data-processing systems (Spark on YARN / k8s / EMR).

---

## Trigger passage (OSTEP, CFS chapter)

> To achieve its efficiency goals, CFS aims to spend very little time making
> scheduling decisions, through both its inherent design and its clever use of
> data structures well-suited to the task. Recent studies have shown that
> scheduler efficiency is surprisingly important; specifically, in a study of
> Google datacenters, Kanev et al. show that even after aggressive optimization,
> scheduling uses about 5% of overall datacenter CPU time [K+15]. Reducing that
> overhead as much as possible is thus a key goal in modern scheduler architecture.

Reference: Kanev et al., "Profiling a Warehouse-Scale Computer," ISCA 2015 —
coins the term **datacenter tax** for low-level building blocks (RPC,
serialization, memory allocation, compression, hashing, kernel/scheduling) that
together consumed ~30% of all cycles. Scheduling is one slice (~5%).

### Q1: How is a ~5% improvement justified as impactful work?

Reframe: don't read 5% as a human-scale percentage. Judge it as
**absolute magnitude × leverage**.

- **Absolute size.** 5% of a ~1M-server fleet ≈ 50,000 servers' worth of CPU
  spent purely deciding what to run next. A 20% efficiency gain reclaims ~10,000
  servers — tens of millions of dollars/year, recurring, growing with the fleet.
- **Datacenter tax = pure overhead.** It generates no business value, so
  reducing it has no tradeoff against product work — every recovered cycle is
  strictly money back.
- **Leverage / zero adoption cost.** The scheduler is on the critical path of
  *everything*. A 2% scheduler win helps every machine, every workload, every
  team instantly, with no migration effort. "Optimize the common path" / Amdahl's
  Law taken to its extreme — the component used by 100% of execution is the
  highest-value target.
- **Clean ROI.** A small efficiency team costs a few $M/year; recovering tens of
  $M is a permanent, measurable 10x+ return. The business case writes itself.
- **Beyond CPU cycles.** Better scheduling also improves tail latency (p99/p999,
  tied to revenue) and capacity in a supply-constrained world (efficiency = more
  compute without new datacenters or scarce silicon/power). Wins compound over
  all future workloads.

Takeaway: **impact ≈ size of the pie × fraction you can move × breadth it applies
to.** A small percentage on a universal, always-on component beats a large
percentage on anything narrow.

---

## OS scheduling baseline (from the chapter)

- Focus: fairness across processes, low scheduler overhead, and **context switch**
  as the ultimate tool — preemptively stop a running process and switch to
  another.
- Round Robin optimizes **response time**; SJF/FIFO optimize **turnaround time**.
- Interactive OSes optimize response time (a human is waiting) → they time-slice.

### Q2: How do these concepts extend to Spark + YARN/k8s/EMR?

Observation that prompted the question: an at-capacity EMR cluster just makes new
jobs **wait** — no preemptive context switch happens. And k8s places pods using
`requests`/`limits` vs node availability, seemingly *not* assuming a 1-CPU node
can run many 1-CPU pods via OS time-slicing. Why don't OS scheduling concepts
show up in distributed job frameworks?

**Reframe: there are two scheduling layers, not one.**

- **Execution scheduling (OS layer):** OS scheduling does NOT disappear. When a
  Spark executor runs on a node, its threads are still context-switched by CFS;
  cgroups (`cpu.shares` / `cfs_quota`) still time-slice it against neighbors.
  Governs "given work is on this box, who's on the CPU this millisecond."
- **Placement / admission scheduling (distributed layer):** YARN/k8s/Spark add a
  higher layer — "*where* does this work go, and *when* is it allowed to start at
  all." This layer deliberately does NOT time-slice.

**Master principle — preemption economics:**

> Preempt aggressively when `cost(save + restore state) ≪ quantum of useful work`.
> Otherwise, don't preempt — queue, or kill-and-redo.

| | OS thread | Spark task / container / pod |
|---|---|---|
| State to preserve | registers + stack: ~KB; memory stays resident | sort buffer / hash aggregation / shuffle blocks / cached partitions: **MB–GB** |
| Save/restore cost | ~1–5 µs (register swap) | seconds–minutes (checkpoint to disk/network); often impossible without app cooperation |
| Useful quantum | ~ms (CFS `sched_latency` ≈ 6 ms) | seconds–minutes |
| Overhead ratio | µs / ms → <1% → **time-slice freely** | seconds / seconds → **catastrophic** → don't |

The context switch is the OS's ultimate tool *because* the register file is small
and memory stays in RAM. A Spark task's "register file" is its multi-GB working
set — no cheap snapshot/resume. So the ultimate tool has to change.

**What distributed schedulers use instead of the context switch:**

1. **Admission control / queueing** — don't start work you can't run to
   completion. EMR-at-capacity: jobs wait, because starting them would either
   force killing in-flight work (wasted) or time-slice everyone slower + risk
   memory blowup. Batch optimizes **turnaround**, not response time → drifts back
   to FIFO / run-to-completion + queueing.
2. **Elastic resource allocation** — instead of time-slicing *fixed* resources
   harder, change *how many* resources each job holds. Spark **dynamic
   allocation** adds/removes executors on pending-task backlog; EMR managed
   scaling adds nodes. Flip of the OS assumption: OS = resources fixed, work
   elastic-in-time; cluster = work lumpy, resources elastic. This removes most of
   the *need* to time-slice.
3. **Kill-and-recompute (preemption without resume)** — YARN Capacity/Fair
   schedulers and k8s priority/preemption DO preempt, but "preempt" = **kill the
   container/pod**, not pause it. Work is lost and recomputed. Used sparingly
   (only to enforce fairness/priority/queue-shares) because killing wastes work.
   The real OS contrast: OS preempts and **resumes**; cluster preempts and
   **redoes**.
4. **App-level checkpointing** — the only route to resumable preemption: the app
   cooperates (Spark `checkpoint()`, structured-streaming checkpoints).
   Transparent OS-style checkpoint/restore (CRIU) exists but is rarely used here.
   The cost just moved into the app.

**Fairness still shows up, generalized:**

- **Spark FAIR scheduler mode** round-robins *tasks* across pools so a big job
  doesn't starve a small one — but at the granularity of **whole tasks that run
  to completion**, not preemptive slices.
- **DRF (Dominant Resource Fairness)** in Fair Scheduler / Mesos generalizes
  fairness from a scalar (CPU time) to a **vector** (CPU AND memory AND ...).

**Distributed-only concepts with no real OS analog:**

- **Data locality / delay scheduling** (Zaharia et al.): Spark deliberately waits
  a few seconds for a slot *near the data* (`PROCESS_LOCAL` → `NODE_LOCAL` →
  `RACK_LOCAL` → `ANY`) rather than run immediately far from it. Faint analog =
  cache/NUMA affinity, but cost ratio differs wildly (cache miss = ns; shipping a
  GB partition = seconds). Locality dominates distributed scheduling as it never
  does in CFS.
- **Speculative execution**: launch a duplicate of a straggler task, take
  whichever finishes first. No OS analog — single-machine threads don't randomly
  run 10× slow from one bad disk / hot node.
- **Gang / all-or-nothing scheduling**: a Spark stage often needs *N* slots
  simultaneously (a shuffle can't half-start). The OS never needs "schedule these
  200 threads at once or none."

### Q3: k8s — why not assume a 1-CPU node runs many 1-CPU pods via time-slicing?

Two corrections:

**(a) It DOES time-slice CPU — `requests` vs `limits` is the knob.**
- `requests` = what the *scheduler* reserves for bin-packing (sum of requests ≤
  node allocatable). The guaranteed floor.
- `limits` = the ceiling the *runtime* (cgroups/CFS) enforces.
- CPU `request=0.1, limit=1` → reserve 0.1 for placement math, burst to a full
  CPU when idle. You CAN pack ~10 such pods on a 1-CPU node; CFS time-slices them
  under contention — exactly the OS behavior. So k8s exposes time-slicing as a
  **declared policy** (`requests < limits` → **Burstable** QoS), not an implicit
  assumption. `request == limit` is the safe default for **performance isolation /
  SLOs** — otherwise a latency-sensitive pod could be starved by a noisy neighbor
  and you could never promise anything. The platform hands the operator the risk
  dial (QoS classes) instead of guessing.

**(b) CPU and memory are fundamentally different resource types:**

| | CPU — **compressible** | Memory — **incompressible** |
|---|---|---|
| Oversubscribe? | Yes — CFS just slows everyone | No — no "memory context switch" |
| Contention outcome | graceful slowdown (throttling) | **OOM kill** — death, not slowdown |
| k8s treatment | `requests` soft (shares); can burst | `requests` effectively a hard reservation |

CPU is a *rate* — share across time and nobody dies, they just go slower. Memory
can't be time-sliced: two pods each needing 1 GB on a 1 GB node can't take turns
"having" the GB (swap is the theoretical out but catastrophically slow, normally
disabled in k8s). The OOM killer just terminates one. So memory must be
bin-packed conservatively; CPU is the one k8s will overcommit. The instinct "a
1-CPU node should run many 1-CPU pods" is **correct for CPU + burst** — expressed
via `requests < limits`.

Far end of the spectrum (opposite of the assumption): the **CPU Manager `static`
policy** pins **Guaranteed** pods with *integer* CPU requests to *exclusive* cores
and turns time-slicing **OFF** for them — for workloads that can't tolerate jitter.

---

## One-line takeaways

- **Preemption:** OS scheduling time-slices fixed resources among elastic,
  cheap-to-preserve work; cluster scheduling allocates elastic resources among
  lumpy, expensive-to-preserve work — so preempt-and-resume is replaced by
  queueing, elastic scaling, and kill-and-recompute. CPU is the exception
  (compressible), so k8s/cgroups still time-slice it underneath — opt in with
  `requests < limits`.
- **Resources:** compressible (CPU — throttle) vs incompressible (memory — OOM)
  is the root reason k8s treats `requests`/`limits` asymmetrically.
