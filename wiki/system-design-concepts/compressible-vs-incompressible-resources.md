# Compressible vs Incompressible Resources

The distinction that explains why Kubernetes treats CPU and memory asymmetrically — why you can overcommit one and not the other, and what `requests` vs `limits` actually mean.

> **CPU is compressible** (a *rate* — oversubscribe and everyone just slows down). **Memory is incompressible** (a *quantity* — oversubscribe and something dies).

## The core difference

| | CPU — **compressible** | Memory — **incompressible** |
|---|---|---|
| Can you oversubscribe? | Yes — CFS just slows everyone | No — there is no "memory context switch" |
| Contention outcome | graceful slowdown (throttling) | **OOM kill** — death, not slowdown |
| k8s treatment | `requests` are soft (shares); can burst | `requests` are effectively a hard reservation |

CPU is a rate: share it across time and nobody dies, they just go slower. You **cannot** time-slice memory — two pods each needing 1 GB on a 1 GB node can't take turns "having" the GB. (Swap is the theoretical escape hatch, but it's catastrophically slow and normally disabled in k8s.) When memory is overcommitted, the OOM killer simply terminates a process. So memory must be bin-packed conservatively; CPU is the resource k8s will happily overcommit.

## requests vs limits

Two numbers that mean different things and are enforced by different components:

- **`requests`** = what the **scheduler** reserves for bin-packing. The sum of requests on a node must be ≤ node allocatable. This is the *guaranteed floor* and the number placement decisions are made against.
- **`limits`** = the ceiling the **runtime** (cgroups + CFS) enforces at execution time.

Because CPU is compressible, `requests` and `limits` can legitimately differ. Because memory is incompressible, a memory `request` behaves as a hard reservation regardless of the limit.

## k8s does *not* ignore OS time-slicing — it exposes it as policy

A natural question: *if a node has 1 CPU, why doesn't k8s just run many 1-CPU pods and let the OS time-slice them?* It can — that's exactly what `requests < limits` expresses.

- Set CPU `request=0.1, limit=1`: you're telling k8s *"reserve 0.1 for placement math, but let me burst to a full CPU when it's idle."*
- Now ~10 such pods pack onto a 1-CPU node, and **CFS time-slices them under contention** — precisely the OS behavior. This is **Burstable** QoS.

So k8s doesn't assume time-slicing implicitly; it makes you **declare** it. The reason `request == limit` (**Guaranteed** QoS) is the safe default is **performance isolation / SLOs**: if the platform silently assumed everything time-slices fine, a latency-sensitive pod could be starved by a noisy neighbor and you could never promise anything. The platform hands the *operator* the risk dial (QoS classes) rather than guessing.

### QoS classes (the risk dial)

| QoS class | Condition | Meaning |
|---|---|---|
| **Guaranteed** | `requests == limits` for CPU *and* memory | strongest isolation; evicted last |
| **Burstable** | at least one `request` set, `request < limit` | opt-in overcommit; can burst, can be throttled/evicted under pressure |
| **BestEffort** | no requests or limits | uses scraps; evicted first |

## The far end: turning time-slicing off entirely

The opposite of "share a CPU across many pods" is the **CPU Manager `static` policy**: it pins **Guaranteed** pods that request *integer* CPUs to *exclusive* cores and turns time-slicing **off** for them — for workloads (low-latency, real-time, heavy cache-locality) that can't tolerate the jitter of being scheduled on and off a shared core at all.

So the full spectrum on one node:

```
BestEffort ── Burstable (request<limit) ── Guaranteed (request==limit) ── CPU Manager static (exclusive cores)
  most overcommit / least isolation  ─────────────────────────────────►  no overcommit / most isolation
```

## Interview angle

> "CPU is compressible and memory isn't — that's the whole reason k8s treats them differently. `requests` is what the scheduler reserves for bin-packing; `limits` is the ceiling cgroups/CFS enforces. For CPU you can set request below limit (Burstable) and pack many pods on a node, letting the OS time-slice them — so k8s isn't ignoring OS scheduling, it's exposing it as a declared policy. For memory there's no time-slicing: overcommit means an OOM kill, so the request is effectively a hard reservation. `request == limit` is the isolation default, and the CPU Manager static policy is the extreme that pins pods to exclusive cores and turns time-slicing off."

## Connections
- [[system-design-concepts/preemption-economics]] — CPU is the "exception" noted there: the resource the cluster still time-slices via cgroups/CFS, precisely because it's compressible
- [[system-design-concepts/work-distribution]] — bin-packing `requests` onto nodes is the overload-avoidance side of distributing work across a fleet
- Builds on OS scheduling theory — CFS throttling and cgroups (`cpu.shares` / `cfs_quota`) are the mechanism that makes CPU compressible in the first place

## Sources
- [[sources/docs/os-scheduling-to-distributed-scheduling]] — the k8s `requests`/`limits` and compressible/incompressible discussion, grounded in the OSTEP CFS chapter
