# Concurrency Primitives: Processes vs Threads vs Green Threads

Three units of concurrent execution, three points on a cost/isolation curve. The axis that actually separates them is **the cost of a context switch** — and that cost is dominated by *what memory state the switch invalidates*, not by the scheduler bookkeeping. Get this mapping right and "why can one machine hold 5M connections but not 5M threads" answers itself.

## The three, by context-switch cost

| Unit | Scheduler | Memory / stack | Context-switch cost | Best for |
|---|---|---|---|---|
| **OS process** | Kernel | Own virtual address space + page tables | **Highest** — user→kernel mode switch **+ TLB flush** → cache-cold address translation afterward | Security sandboxing, fault isolation, bypassing a GIL (Python `multiprocessing`) |
| **OS thread** | Kernel | Shares parent's address space; fixed pre-allocated kernel stack (~1–2 MB) | **Moderate** — mode switch, but **no TLB flush** (same address space) | CPU-bound work (1:1 to cores), blocking FFI/C calls |
| **Green thread** | Language runtime (user space) | M:N onto OS threads; growable heap stack (~300 B in Erlang) | **Lowest** — no mode switch, runtime just swaps instruction pointers | I/O-bound work, massive concurrency (millions of sockets/WebSockets) |

## Why the TLB flush is the crux
Every memory access goes through virtual→physical translation, cached in the **TLB**. Switching between *processes* changes the address space, so the TLB must be flushed — and for a while after the switch, every memory access misses the TLB and pays a page-table walk. That post-switch cache-cold penalty, not the register save, is what makes process switches expensive. **Threads share the address space, so no flush** — that's the single biggest reason threads are cheaper than processes. Green threads never enter the kernel at all, so they skip mode-switch *and* flush.

## Why green threads scale to millions
- **Stack size:** an OS thread pre-reserves ~1–2 MB of kernel stack; a million threads is terabytes of stack you don't have. A BEAM process starts at ~300 bytes and grows on the heap — millions fit in RAM.
- **M:N multiplexing:** the runtime schedules M green threads onto N OS threads (N ≈ core count). A green thread blocked on I/O is parked in user space and its OS thread runs another — no kernel involvement, so blocking is "free."
- **The catch:** green threads give *concurrency*, not extra *parallelism* — real CPU parallelism is still capped at N cores. And one green thread doing a long CPU-bound (or blocking-FFI) computation without yielding can starve others on its scheduler — which is why BEAM preemptively de-schedules on a reduction count, and why blocking C calls want a real OS thread.

This is the same **preemption-cost logic** as [[system-design-concepts/preemption-economics]]: preempt cheaply where saving/restoring state is cheap (green threads, in user space) and reluctantly where it's expensive (processes, with a TLB flush) — the cost of a switch is what dictates the scheduling design at each layer.

## Key points
- Rank by context-switch cost: process (mode switch + **TLB flush**) > thread (mode switch, no flush) > green thread (no kernel at all).
- The TLB flush + cache-cold aftermath — not register bookkeeping — is why process switches are the most expensive.
- Green threads scale to millions because of tiny growable stacks (~300 B) + M:N user-space scheduling; threads don't because of ~1–2 MB fixed kernel stacks.
- Green threads buy concurrency for I/O-bound load, not parallelism — CPU parallelism is still bounded by the N backing OS threads.
- Match the tool: process → isolation/security; thread → CPU-bound & blocking FFI; green thread → massive I/O-bound concurrency.

## Interview angle

> "There are three execution units and the thing that separates them is context-switch cost. An OS process has its own address space, so switching it needs a mode switch *and* a TLB flush — after which memory accesses are cache-cold, and that's the real cost. A thread shares the address space, so mode switch but no flush. A green thread is scheduled in user space by the runtime with no kernel transition at all, on tiny ~300-byte growable stacks multiplexed M:N onto core-count OS threads — which is why one machine holds millions of them for WebSockets. The catch is green threads give concurrency, not parallelism, and a CPU-bound one can starve its scheduler unless the runtime preempts it."

## Connections
- [[theory/actor-model-message-passing]] — BEAM processes *are* green threads; that page covers their memory/messaging, this one their scheduling
- [[system-design-concepts/preemption-economics]] — same governing rule: preempt where state save/restore is cheap; the context-switch cost here is that rule at the OS layer
- [[system-design-concepts/compressible-vs-incompressible-resources]] — CPU (time-sliceable) vs memory (not) mirrors why thread stacks are a hard cap while CPU is overcommittable
- [[system-design-concepts/message-fanout]] — "single-threaded per process" and reduction-count de-scheduling are why fan-out from one process is a bottleneck

## Sources
- [[sources/articles/discord-scaling-elixir-5m-concurrent]] — "OS Primitives: Processes vs. Threads vs. Green Threads" (companion deep-dive)
- [How Discord Scaled Elixir to 5,000,000 Concurrent Users](https://discord.com/blog/how-discord-scaled-elixir-to-5-000-000-concurrent-users) — original article
