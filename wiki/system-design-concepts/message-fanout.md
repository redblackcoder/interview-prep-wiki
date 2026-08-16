# Message Fan-out Without a Single Hot Process

Real-time pub/sub — one publish delivered to N connected subscribers — has a recurring failure mode: **the fan-out runs through one coordinating process, and that process becomes the bottleneck.** Discord's Elixir/BEAM scaling story is three variations on the *same* fix, and the unifying lesson is portable to any actor/event system: *distribute the send, share the read, gate the caller — never funnel a hot path through one process.*

## The problem: fan-out from one process is O(N) serial work
A guild (chat server) process publishing to 30,000 sessions calls `send/2` 30,000 times. On BEAM each `send/2` is 30–70μs *and* de-schedules the caller (a process is effectively single-threaded), so a single publish took **0.9–2.1s** at peak. Spawning a process per publish breaks **linearizability** (clients depend on in-order events), and sharding the guild process is a huge undertaking. The real move is to distribute the *send itself*.

## Fix 1 — Manifold: push fan-out to the destination nodes
Instead of the origin process sending to every remote PID directly:
1. **Group target PIDs by their node.** The origin makes at most *one* cross-node send per node (not one per PID) — collapsing network traffic.
2. **Per-node partitioner.** Each node runs a `Manifold.Partitioner` that receives the bulk message and **consistently hashes** PIDs (`:erlang.phash2/2`) across worker processes mapped to CPU cores.
3. **Workers do the final `send`.** Parallelized across cores, so no single process is the fan-out chokepoint.

**Linearizability is preserved** because deterministic hashing routes a given PID to the *same* worker every time, so its messages never reorder. Net effect: CPU cost of fan-out spread across cores *and* nodes, and cross-node network traffic sharply reduced. (Open-sourced as [Manifold](https://github.com/discordapp/manifold).)

## Fix 2 — FastGlobal: share read-only state without copying
Fan-out needs a routing ring ([[theory/consistent-hashing]]) that every session reads constantly. The bottleneck *evolved* as each obvious fix hit a new wall:

| Approach | Wall hit | Time |
|---|---|---|
| C port owning the ring (IPC) | BEAM forces single-process ownership of the port → 500k processes queue on 1 process | ~30 s |
| Pure Elixir + ETS | ETS allows concurrent reads but **deep-copies** the term out on every read (~7μs, huge for a big ring) | ~17.5 s |
| **FastGlobal / mochiglobal** | Compile the ring into a module as a **constant** → VM keeps code literals in a **read-only shared heap**, read with no copy (~0.3μs) | ~0.75 s |

The trick: a function that always returns the same constant lets the VM store the data in a shared, copy-free heap. Trade-off — rebuilding the module costs ~1s, so it's only for **rarely-mutated** data (the ring changes seldom). (See [[theory/actor-model-message-passing]] for *why* ETS copies: isolation. FastGlobal is the sanctioned escape, like the >64B binary heap.) Open-sourced as [FastGlobal](https://github.com/discordapp/fastglobal).

## Fix 3 — Semaphore: gate the caller before a doomed request
Removing the slow-lookup bottleneck removed its accidental back-pressure, so ~5M sessions could **stampede** ~10 registry processes ([[system-design-concepts/rate-limiting|thundering herd]]): timeouts queue → clients retry → mailboxes balloon → OOM → cascading outage. Fix: a **node-local semaphore** so a session bails *before* issuing a request that's bound to time out. Implemented with `:ets.update_counter/4` — atomic, `write_concurrency`, a BIF running in user space (<1μs), *not* a syscall and *not* a coordinating process. Chosen over a **circuit breaker** because a breaker trips fully open (zero attempts for a window); a semaphore *shapes* traffic to a safe concurrency instead. (Open-sourced as [Semaphore](https://github.com/discordapp/semaphore).)

**Recovery-time math:** with N processes needing to reconnect and a concurrency limit C, recovery ≈ `(N / C) × T_trip`. E.g. 10,000 / 10 × 2ms = 2s to drain safely — bounded, and the downstream is never overwhelmed.

## The through-line
> All three are the same principle: **don't route a hot path through one coordinating process.** Distribute the send (Manifold), share the read copy-free (FastGlobal), gate the caller locally (Semaphore). Whenever a design has "everyone talks to the one process that owns X," that's the bottleneck to design out.

## Key points
- Naive fan-out is O(N) serial sends from one single-threaded process — the origin, not the network, is the bottleneck.
- Manifold: group-by-node (one send per node) + per-node partitioner hashing PIDs across cores; deterministic hashing keeps events linearizable.
- FastGlobal: compile rarely-changing shared data into a module constant → read-only shared heap → copy-free reads; ~1s rebuild is the trade.
- Semaphore (node-local, `ets.update_counter`, <1μs BIF) sheds load *before* the doomed call; preferred over a circuit breaker because it shapes rather than fully stops traffic.
- Recovery time under shedding ≈ (N/C)×T_trip — a closed form you can quote.

## Interview angle

> "Fan-out to N subscribers naively means N serial sends from one process, and on an actor runtime that process is single-threaded, so a 30k-member broadcast took seconds. The fix is to stop funneling through one process, three ways. Manifold groups recipients by node so you send once per node, then a per-node partitioner hashes recipients across cores — and because the hash is deterministic, ordering is preserved. FastGlobal handles the shared routing table: instead of copying it out of ETS on every read, you compile it into a module constant so the VM serves it from a read-only shared heap, copy-free. And a node-local ETS semaphore sheds load before a doomed request so a stampede can't OOM the box — a semaphore not a circuit breaker, because you want to shape traffic, not stop it dead."

## Connections
- [[system-design-concepts/work-distribution]] — Manifold is fan-out's version of "partition work across a fleet without a central bottleneck"
- [[theory/consistent-hashing]] — the routing ring FastGlobal serves, and the `phash2` hashing Manifold's partitioner uses
- [[theory/actor-model-message-passing]] — why `send/2` copies and why ETS reads copy; FastGlobal is the sanctioned copy-free escape
- [[theory/concurrency-primitives]] — "single-threaded per process" + reduction-count de-scheduling is why one process can't fan out fast
- [[system-design-concepts/rate-limiting]] — the semaphore is back-pressure/load-shedding; the stampede is a thundering herd, and the recovery formula sharpens it
- [[system-design-concepts/read-state-watermarking]] — the other half of Discord's real-time design: making the delivered messages durable and multi-device-consistent

## Sources
- [[sources/articles/discord-scaling-elixir-5m-concurrent]] — "Message Fanout", "Fast Access Shared Data", "Limited Concurrency" + companion deep-dive §1, §3, §5
- [How Discord Scaled Elixir to 5,000,000 Concurrent Users](https://discord.com/blog/how-discord-scaled-elixir-to-5-000-000-concurrent-users) — original article; libraries: [Manifold](https://github.com/discordapp/manifold), [FastGlobal](https://github.com/discordapp/fastglobal), [Semaphore](https://github.com/discordapp/semaphore)
