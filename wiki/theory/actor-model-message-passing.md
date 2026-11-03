# Actor-Model Message Passing: Copy vs Share

The actor model gives each process an isolated heap and a mailbox; processes never share mutable memory — they communicate only by sending messages. That isolation is what buys the actor model its headline properties, but *how* a runtime moves a message between actors — **copy it, or share a pointer** — is the design fork that determines GC behavior, latency, and what discipline the programmer must keep.

## Erlang/BEAM — strict isolation by copying

Every BEAM process has its own tiny heap and its own garbage collector. Sending a message **deep-copies** the data (a low-level C `memcpy`) into the recipient's mailbox. The payoffs of never sharing:

- **Micro-GC, no stop-the-world.** Heaps are small and per-process, so a collection pauses *one* process for microseconds — never the whole VM. This is the property that lets one node hold millions of live processes with predictable latency.
- **Fault isolation.** A process crashing (or being killed) can't corrupt another's heap; supervisors restart it clean.

The cost is the copy itself — real on a hot path (see [[system-design-concepts/message-fanout]], where `send/2` ran 30–70μs and de-scheduled the caller).

### The large-binary loophole
Deep-copying a large payload (an image, a long message) on every send would be ruinous, so BEAM makes one exception: **binaries larger than 64 bytes live in a process-external, reference-counted shared heap.** Processes pass a lightweight *reference*, not the bytes; the VM GCs the binary when the last reference drops. So the model is "copy everything small; share (by refcount) everything big" — isolation preserved for the mutable small stuff, copy avoided for the bulk.

## JVM / Akka — the same model by sharing pointers

Akka recreates actor semantics on the JVM, but the JVM has **one big shared heap** with locks/mutexes as the native concurrency tool. Copying every message the BEAM way would be expensive (JVM objects are large; GC is generational stop-the-world-ish). So Akka inverts the trade:

- **Local sends pass a pointer**, not a copy — cheap, but two actors now reference the same object.
- **The "honor system":** safety depends on the programmer using **immutable** messages (records/case classes). If neither actor can mutate the shared object, there's no race — you get isolation's *effect* without paying for a copy. Break immutability and you're back to shared-mutable-state bugs the model was supposed to prevent.
- **Remote sends serialize** (deep-copy over TCP) — so across the network, Akka behaves exactly like Erlang; the pointer optimization is a local-only special case.

## The trade in one line
> BEAM guarantees isolation *at runtime* by copying (with a shared-heap escape hatch for big binaries); Akka achieves it *by convention* (immutability) so it can pass pointers locally. One is enforced and pays copy cost; the other is faster but trusts the programmer.

## Key points
- Actor isolation is the source of micro-GC and fault-tolerance — the absence of shared mutable state is the whole point.
- BEAM copies messages (`memcpy`) — cheap for small terms, and small per-process heaps make GC pauses microscopic and local.
- Binaries >64B are the exception: ref-counted shared heap, passed by reference — copy avoidance without giving up isolation.
- Akka gets actor semantics on a shared-heap VM by passing pointers + requiring immutable messages; it serializes (copies) only across the network.
- "Enforced isolation (copy) vs. isolation-by-convention (immutability)" is the portable insight for any message-passing system.

## Interview angle

> "The actor model's power — micro-second per-process GC, fault isolation — comes from never sharing mutable memory. Erlang enforces that by deep-copying every message into the recipient's mailbox, with one escape hatch: binaries over 64 bytes go to a ref-counted shared heap and are passed by reference, so you don't copy a whole image on every send. Akka gives you the same actor model on the JVM, but the JVM has one shared heap, so it passes pointers locally and relies on you using immutable messages — isolation by convention instead of by copy — and only serializes when a message crosses the network."

## Connections
- [[theory/concurrency-primitives]] — actors are green threads (BEAM processes); this is the *memory/messaging* half, that page is the *scheduling* half
- [[system-design-concepts/message-fanout]] — the copy cost of `send/2` is exactly what makes naive fan-out expensive and motivates Manifold
- [[theory/pure-functional-programming]] — immutability is what makes Akka's pointer-passing safe; the same property that makes pure FP concurrency-friendly
- [[tech/elm]] — another BEAM-adjacent / message-driven model (TEA) built on immutable state
- [[theory/copy-on-write-vs-mvcc]] — same underlying question — when to copy vs share a view of data — one layer down

## Sources
- [[sources/articles/discord-scaling-elixir-5m-concurrent]] — "Erlang Message Passing vs. JVM (Akka)" and the message-fanout sections
- [How Discord Scaled Elixir to 5,000,000 Concurrent Users](https://discord.com/blog/how-discord-scaled-elixir-to-5-000-000-concurrent-users) — original article
