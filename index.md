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
- [[wiki/system-design-concepts/rds-vs-key-value-store]] — Relational vs KV as the primary store: durable SSD bytes vs fast DRAM bytes
- [[wiki/system-design-concepts/cloud-database-cost-model]] — The four cost axes (instance/storage/IO/requests) and the ~100–250× RAM-vs-SSD gap
- [[wiki/system-design-concepts/rate-limiting]] — Counter location × window shape × failure trade-off; local vs global, L4 vs L7

### Theory
- [[wiki/theory/consistent-hashing]] — Stable partition assignment with minimal key movement on node changes
- [[wiki/theory/bloom-filters]] — Probabilistic set membership for space-efficient dedup
- [[wiki/theory/pure-functional-programming]] — Pure functions, immutability, expressions-over-statements
- [[wiki/theory/folds-and-tail-recursion]] — foldl vs foldr, and how TCO turns recursion into a loop
- [[wiki/theory/osi-model]] — The 7-layer model and encapsulation; where TLS and VPNs sit
- [[wiki/theory/durability-rpo-rto]] — RPO/RTO, event sourcing, and the non-idempotent replay hazard on crash recovery
- [[wiki/theory/copy-on-write-vs-mvcc]] — Isolating concurrent views: base+delta overlays (git worktrees) vs. versioned snapshots
- [[wiki/theory/rate-limiting-algorithms]] — The five window shapes (fixed/sliding-log/sliding-counter/token/leaky) and their trade-offs

### Tech
- [[wiki/tech/elm]] — Pure functional front-end language; the Elm Architecture (TEA) state loop
- [[wiki/tech/https-tls]] — HTTPS over TLS: handshakes, cipher suites, PKI, forward secrecy
- [[wiki/tech/vpn]] — Layer-by-layer VPN build; tunneling, IPsec/OpenVPN/WireGuard, defense in depth
- [[wiki/tech/aws-rds-postgresql]] — Managed Postgres: Multi-AZ topologies & node counts, gp3/io2, `synchronous_commit`
- [[wiki/tech/aws-elasticache-redis]] — Managed Redis/Valkey: persistence semantics, node vs serverless, data tiering
- [[wiki/tech/envoy-ratelimit-service]] — The global RLS: gRPC + descriptor tree + Redis; identity-agnostic, edge vs mesh
- [[wiki/tech/istio-service-mesh]] — CRDs→Envoy via istiod; mTLS/SPIFFE authz; EnvoyFilter escape hatch

### Coding Patterns
- [[wiki/coding-patterns/fold-accumulator]] — Reduce a list via a threaded accumulator: naive → tail-recursive → fold

## Statistics
- Total wiki pages: 28
- Total sources: 8
- Last updated: 2026-08-12
