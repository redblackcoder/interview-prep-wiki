# Interview Prep — Index

## Categories

- [[wiki/theory/|Theory]] — CS fundamentals
- [[wiki/system-design-concepts/|System Design Concepts]] — Patterns from design problems
- [[wiki/tech/|Tech]] — Technologies and platforms
- [[wiki/algorithms/|Algorithms]] — CS algorithms
- [[wiki/coding-patterns/|Coding Patterns]] — Patterns from coding problems

## Pages

### System Design Concepts
- [[wiki/system-design-concepts/web-crawler]] — Distributed web crawler architecture and design trade-offs
- [[wiki/system-design-concepts/work-distribution]] — Partitioning work across a fleet without starvation or redundancy
- [[wiki/system-design-concepts/preemption-economics]] — Why distributed schedulers queue/scale/kill instead of context-switching
- [[wiki/system-design-concepts/compressible-vs-incompressible-resources]] — Why k8s overcommits CPU but hard-reserves memory (requests vs limits)
- [[wiki/system-design-concepts/network-security-layers]] — Payload vs envelope: why HTTPS isn't access control, with a concrete attack
- [[wiki/system-design-concepts/zero-trust-ztna]] — Beyond perimeter VPNs: per-request, per-app authorization
- [[wiki/system-design-concepts/context-assembly-retrieval-ladder]] — Projecting a huge corpus into a bounded context window via a cost-tiered retrieval ladder
- [[wiki/system-design-concepts/agent-tool-sandboxing]] — Permission ≠ isolation; model judgment is never a security boundary; containment over policy
- [[wiki/system-design-concepts/agent-loop]] — The LLM agent as a reactive state machine: typed blocks, tool_use pairing, verification as tool calls

### Theory
- [[wiki/theory/consistent-hashing]] — Stable partition assignment with minimal key movement on node changes
- [[wiki/theory/bloom-filters]] — Probabilistic set membership for space-efficient dedup
- [[wiki/theory/pure-functional-programming]] — Pure functions, immutability, expressions-over-statements
- [[wiki/theory/folds-and-tail-recursion]] — foldl vs foldr, and how TCO turns recursion into a loop
- [[wiki/theory/osi-model]] — The 7-layer model and encapsulation; where TLS and VPNs sit
- [[wiki/theory/durability-rpo-rto]] — RPO/RTO, event sourcing, and the non-idempotent replay hazard on crash recovery
- [[wiki/theory/copy-on-write-vs-mvcc]] — Isolating concurrent views: base+delta overlays (git worktrees) vs. versioned snapshots

### Tech
- [[wiki/tech/elm]] — Pure functional front-end language; the Elm Architecture (TEA) state loop
- [[wiki/tech/https-tls]] — HTTPS over TLS: handshakes, cipher suites, PKI, forward secrecy
- [[wiki/tech/vpn]] — Layer-by-layer VPN build; tunneling, IPsec/OpenVPN/WireGuard, defense in depth

### Coding Patterns
- [[wiki/coding-patterns/fold-accumulator]] — Reduce a list via a threaded accumulator: naive → tail-recursive → fold

## Statistics
- Total wiki pages: 20
- Total sources: 5
- Last updated: 2026-08-04
