---
source: docs/os-scheduling-to-distributed-scheduling.md
source_url: https://github.com/redblackcoder/interview-prep-raw/blob/main/docs/os-scheduling-to-distributed-scheduling.md
type: doc
date_extracted: 2026-07-01
topic: system-design-concepts
---

# OS Scheduling → Distributed Data-Processing Scheduling

## Key Ideas
- **The same scheduling theory produces opposite-looking designs at different layers** because the *economics of preemption* invert as you go up the stack. OS scheduling doesn't disappear in Spark/YARN/k8s — it still runs underneath (CFS/cgroups time-slice executor threads). The distributed layer sits *on top* solving a different problem: **placement/admission** ("*where* does work go, *when* may it start") vs **execution** ("who's on the CPU this millisecond").
- **Preemption economics (the master principle):** preemption is only viable when `cost(save + restore state) ≪ quantum of useful work`. Tiny for OS threads (KB of register/stack state, ~µs switch, ~ms quantum); catastrophic for Spark tasks/pods (MB–GB working sets, seconds–minutes to checkpoint, often impossible without app cooperation). So the context switch — the OS's "ultimate tool" — has to be *replaced*, not inherited.
- **The distributed toolkit that replaces preempt-and-resume:** (1) admission control / **queueing** (EMR-at-capacity jobs wait); (2) **elastic resource allocation** (Spark dynamic allocation, EMR managed scaling); (3) **kill-and-recompute** (YARN Capacity/Fair + k8s preemption *kill* pods, they don't pause); (4) **app-level checkpointing** (`checkpoint()`, CRIU) as the only route to resumable preemption.
- **OS preempts and *resumes*; the cluster preempts and *redoes*.** That's the crisp difference. Killing wastes work, so it's used sparingly — only to enforce fairness/priority/queue-shares, never to interleave for time-sharing.
- **Batch optimizes turnaround, not response time** → it drifts back toward FIFO / run-to-completion + queueing (straight from the OSTEP RR-vs-SJF tradeoff). Interactive OSes optimize response time (a human waits) → they time-slice.
- **Fairness survives, generalized:** Spark FAIR mode round-robins *whole tasks* across pools (not preemptive slices); **DRF** generalizes scalar CPU fairness to a *resource vector* (CPU AND memory AND …).
- **Compressible vs incompressible resources** is the root reason k8s treats `requests`/`limits` asymmetrically. CPU is a *rate* → oversubscribe and everyone just slows (CFS throttling, graceful). Memory is *incompressible* → oversubscribe and the OOM killer *terminates* a pod (death, not slowdown); there is no "memory context switch," and swap is normally disabled in k8s.
- **k8s does NOT ignore OS time-slicing — it exposes it as declared policy.** `requests` = what the *scheduler* reserves for bin-packing (guaranteed floor, Σrequests ≤ node allocatable); `limits` = the ceiling the *runtime* (cgroups/CFS) enforces. CPU `request<limit` (**Burstable** QoS) lets many 1-CPU-capable pods pack on a 1-CPU node and time-slice under contention. `request==limit` is the safe default for **performance isolation/SLOs** (no noisy-neighbor starvation). Far end: **CPU Manager `static` policy** pins Guaranteed integer-CPU pods to exclusive cores, turning time-slicing OFF for jitter-sensitive workloads.
- **Distributed-only, no OS analog:** data locality / **delay scheduling** (wait for a slot near the data: `PROCESS_LOCAL → NODE_LOCAL → RACK_LOCAL → ANY`), **speculative execution** (duplicate a straggler, take the winner), **gang/all-or-nothing scheduling** (a shuffle needs N slots at once or none).
- **Datacenter tax (Kanev et al., ISCA 2015):** scheduling ≈ 5% of datacenter CPU even after aggressive optimization; part of ~30% spent on low-level building blocks (RPC, serialization, alloc, compression, hashing, kernel). Impact ≈ *size of pie × fraction you can move × breadth it applies to* — a small % on a universal, always-on component beats a large % on anything narrow.

## My Understanding
*(Seeded from the discussion — the starting question already contained the right instinct; revise into your own words on a re-extract.)*
- There are **two schedulers stacked**. The cluster scheduler answers *where / whether to start*; the OS scheduler answers *who runs right now*. They look contradictory (one queues, one time-slices) only if you forget they're solving different questions at different state-preservation costs. OS scheduling never went away — it's still time-slicing executor threads under every Spark job.
- The unlock is reframing the context switch as "cheap **only because** register+stack state is KB and memory stays resident." Once the working set is GBs, "just pause and resume it" stops being free — and every distributed design choice (queue instead of start, scale out instead of share harder, kill-and-redo instead of pause) falls out of that one inequality.
- My original instinct — "shouldn't a 1-CPU node run many 1-CPU pods via time-slicing?" — turns out to be **correct for CPU**, and k8s already supports it: that's exactly `requests < limits` (Burstable). What I was missing is that it's *opt-in* (for SLO isolation) and that it's **CPU-only** — memory can't be time-sliced because it's incompressible (oversubscribe → OOM kill, not slowdown). So the asymmetry in `requests`/`limits` isn't arbitrary; it's the physics of the resource.

## Open Questions
*(Candidates surfaced in discussion — keep whichever actually nag at you on a re-extract.)*
- How does YARN/k8s preemption actually *choose the victim* (priority + fairness deficit / queue over-share), and how does that differ from the OS picking the next thread off its runqueue?
- Where exactly does gang scheduling live in the Spark/k8s world (YARN reservations, k8s coscheduling / Volcano), and what concretely breaks without it beyond "the shuffle can't half-start"?
- Delay-scheduling tuning — how long is it worth waiting for a `NODE_LOCAL` slot before falling back to `ANY`? What signal decides that timeout?

## Connections
- Relates to: [[system-design-concepts/preemption-economics]] — the page this extract seeds (why distributed schedulers queue instead of context-switch)
- Relates to: [[system-design-concepts/compressible-vs-incompressible-resources]] — the page this extract seeds (k8s requests vs limits)
- Relates to: [[system-design-concepts/work-distribution]] — queueing/backpressure/starvation are the admission-layer's tools; this extends that page "downward" into *when* work is allowed to start
- Builds on: OSTEP scheduling chapter — CFS, context switch, RR-vs-SJF (response time vs turnaround), scheduler-overhead / datacenter-tax

## Key Quotes / Annotations
From the OSTEP CFS chapter (trigger passage):
> "…scheduler efficiency is surprisingly important; specifically, in a study of Google datacenters, Kanev et al. show that even after aggressive optimization, scheduling uses about 5% of overall datacenter CPU time [K+15]."

Preemption-economics table (OS thread vs Spark task/pod):
> register+stack (~KB, ~µs switch, ~ms quantum) → time-slice freely;
> MB–GB working set (seconds–minutes to checkpoint, ~seconds quantum) → catastrophic, so queue / scale / kill-and-redo instead.

Compressible vs incompressible:
> CPU — oversubscribe → CFS slows everyone (graceful throttling). Memory — oversubscribe → OOM kill (death, not slowdown). No memory context switch; swap normally disabled in k8s.

k8s knob:
> `requests` = scheduler's bin-packing reservation (Σ ≤ node allocatable); `limits` = cgroups/CFS ceiling. `request<limit` = Burstable = opt-in time-slicing. `request==limit` = isolation/SLO default. CPU Manager `static` = exclusive cores, time-slicing OFF.
