# Local IPC Transports (App → Agent Hop)

When an in-process library ships telemetry to a node-local agent, the transport choice looks like a detail — HTTP on loopback vs a Unix domain socket vs UDP — but it decides the one thing that matters for a metrics path: **when the agent can't keep up, does the app slow down (backpressure) or do samples get dropped (load-shedding)?** For operational, lossy-tolerant data you almost always want load-shedding, and that requirement — not familiarity — should pick the transport.

## The one-sentence mental model

> **The real axis isn't "which is fastest," it's backpressure vs load-shedding. A metrics hop must never harm the app, so you want fire-and-forget with drop-on-full — UDP loopback, or a non-blocking datagram Unix socket — with client-side batching. Reserve reliable/stream transports (HTTP, stream UDS) for hops where you actually want the sender to feel the slowdown.**

## The options

| Transport | Semantics | Overhead | Access control | Fits |
| --- | --- | --- | --- | --- |
| **HTTP over TCP loopback** | Reliable, request/**response** (sender waits) | TCP stack; conn setup; ephemeral-port churn; headers/serialization | anything in the netns can hit the port | low-volume, debuggable, cross-language control planes |
| **UDS `SOCK_STREAM`** | Reliable, ordered, **backpressure** (blocks/buffers when slow) | no TCP/IP; less copying | filesystem perms + `SO_PEERCRED` peer creds | local RPC where you *want* the sender throttled |
| **UDS `SOCK_DGRAM`** | Message-framed; on Linux local dgrams aren't silently lost — they **`ENOBUFS`/`EAGAIN`** when full (you decide to drop) | no TCP/IP | filesystem perms + peer creds | you want framing + explicit drop control |
| **UDP loopback** | **Genuinely fire-and-forget**; kernel drops on full buffer, sender never blocks | minimal | none (netns-local) | high-rate lossy telemetry (statsd model) |

## Why the semantics dominate

A buggy `track.increment` in a tight loop is the stress case:
- **TCP / HTTP / stream UDS:** the send buffer fills, the sender **blocks** (or the agent's receive buffer grows and it **OOMs**). The metrics path has now degraded the application — the opposite of what a monitoring system should do.
- **UDP loopback:** the socket buffer fills and the **kernel drops** the excess; the sender returns immediately. You lose samples you didn't need (the SLA is "spot big moves"), and the app is untouched.
- **Datagram UDS:** in between — you get message boundaries and, on a non-blocking socket, an explicit `EAGAIN` you turn into a drop, so *you* own the shedding policy rather than the kernel.

This is the concrete edge of [[system-design-concepts/edge-shed-vs-core-durability]]: shed here, persist later.

## Secondary trade-offs
- **Overhead:** HTTP's request/response couples app latency to the agent's health and adds header/serialization cost — wrong on a hot path. UDS/UDP skip the TCP/IP stack entirely.
- **Framing:** datagrams (UDP, dgram UDS) give message boundaries free; a stream (TCP, stream UDS) needs your own length-prefix framing; HTTP gives framing at cost.
- **Access control & attribution:** a UDS is a **filesystem path** — mode/uid/gid gate who can write, and `SO_PEERCRED` yields the sender's pid/uid (useful for per-source attribution on a shared DaemonSet). A loopback port is reachable by anything in the network namespace and is harder to attribute.
- **Ports vs paths:** TCP needs a port (coordination, ephemeral-port exhaustion under connection churn); a UDS needs a path (no port coordination, but the path must be mounted — `emptyDir` for a sidecar, `hostPath` for a DaemonSet).
- **Portability:** HTTP is the most universal across languages; UDP is nearly as portable; datagram-UDS support varies more across language stdlibs. For "a library that ships with every service in every language," client ubiquity is a real input.
- **Always batch in the client** regardless of transport: accumulate for ~1s and send aggregated deltas to amortize syscalls and shrink volume before it leaves the process.

## Key points
- **Pick the transport from the delivery semantics you need, not the one you know.** For lossy metrics that must not harm the app: fire-and-forget datagrams, non-blocking, drop-on-full.
- **A UDS lets you *choose* reliable vs lossy** (stream vs datagram) — it's not inherently either.
- **HTTP's request/response is a coupling**, not just overhead: the app waits on the agent. Fine for a low-rate control path, wrong for the hot counter path.
- **Reserve reliability for the agent→collector hop** (retries, standard infra) where you *do* want delivery guarantees; keep the app→agent hop cheap and sheddable.
- **Topology sets the plumbing:** sidecar ⇒ UDS over a shared volume; DaemonSet ⇒ UDS over `hostPath` or UDP to the node IP. See [[system-design-concepts/sidecar-vs-daemonset]].

## Interview angle

> "I wouldn't pick by familiarity — I'd pick by what happens when the agent falls behind. On a metrics path the app must never slow down, so I want load-shedding, not backpressure. HTTP on loopback and a stream Unix socket are both reliable — the sender blocks or the agent's buffer grows and OOMs — which is exactly wrong here. A Unix socket actually lets me choose: stream for reliable-with-backpressure, datagram for message-framed-with-explicit-drop. And UDP loopback is pure fire-and-forget — the kernel drops on a full buffer and the sender never blocks, the statsd model. So: UDP loopback or a non-blocking datagram UDS, drop-on-full, with the client batching ~1s of increments. I'd keep HTTP for the agent→collector hop where I want retries and standard tooling. A UDS also gives me filesystem-permission access control and peer credentials for source attribution, which matters if a shared DaemonSet is receiving from many pods."

## Connections
- [[system-design-concepts/edge-shed-vs-core-durability]] — this hop is the "shed" end of the reliability gradient
- [[system-design-concepts/sidecar-vs-daemonset]] — topology decides socket-over-volume vs socket-over-hostPath vs node-IP UDP
- [[system-design-concepts/metrics-pull-vs-push]] — the StatsD/UDP push model vs a pull scrape; where sampling variance enters
- [[system-design-concepts/red-metrics-exposition]] — the StatsD-over-UDP sidecar + sampling exposition this specializes
- [[system-design-concepts/serving-constrained-resources]] — "shed / degrade" as the response to overload, applied at the transport layer
- [[theory/osi-model]] — why loopback still traverses L3/L4 while a UDS does not
- [[theory/latency-numbers]] — the syscall/copy costs that make batching and stack-skipping worth it

## Sources
- [[sources/docs/design-metrics-counters-mock-interview]] — the loopback-vs-UDS-vs-UDP exchange; the UDP load-shed insight and the backpressure-vs-shedding framing it implies
- [[sources/docs/metrics-collection-observability-self-study]] — StatsD/UDP sidecar, sampling variance, the lossy-push model
