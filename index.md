# Interview Prep — Index

## Categories

- [[wiki/theory/|Theory]] — CS fundamentals
- [[wiki/system-design-concepts/|System Design Concepts]] — Patterns from design problems
- [[wiki/tech/|Tech]] — Technologies and platforms
- [[wiki/algorithms/|Algorithms]] — CS algorithms
- [[wiki/coding-patterns/|Coding Patterns]] — Patterns from coding problems
- [[wiki/behavioral/|Behavioral]] — Interview stories (STAR), one per leadership-principle prompt

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
- [[wiki/system-design-concepts/global-rate-limiting]] — Multi-region counting: consistent vs accurate, CRDT/home-region, why VPN-hopping doesn't bypass
- [[wiki/system-design-concepts/client-identification]] — What to key a limiter on: authenticated credential vs anonymous IP/ASN/JA4; CGNAT and the IPv6 /64 trap
- [[wiki/system-design-concepts/message-fanout]] — Pub/sub fan-out without a hot process: Manifold + FastGlobal + semaphore back-pressure
- [[wiki/system-design-concepts/read-state-watermarking]] — Durable chat delivery (commit-before-ACK) + per-user watermark for multi-device notification dedup
- [[wiki/system-design-concepts/hash-vs-range-partitioning]] — Uniform load vs cheap range scans; the DynamoDB composite-key resolution and the hot-partition trap
- [[wiki/system-design-concepts/hinted-handoff]] — Sloppy quorum: stay writable through AZ+1 at RF=3 without raising the replication factor
- [[wiki/system-design-concepts/read-repair]] — Read-time convergence: heal the hot set for free on divergent quorum reads
- [[wiki/system-design-concepts/anti-entropy-merkle-trees]] — Background convergence for cold keys; Merkle diffing in O(diffs·log N), not O(N)
- [[wiki/system-design-concepts/per-shard-raft]] — Linearizable AND scalable: one Raft group per shard, RF=5 for AZ+1 majority
- [[wiki/system-design-concepts/leaderless-vs-leader-based]] — The write-path fork; the Dynamo 2007→DynamoDB 2012 switch and why
- [[wiki/system-design-concepts/distributed-id-generation]] — Global unique IDs with no per-ID coordination: uniqueness by construction, library-not-service, clock-backward + worker-id split-brain
- [[wiki/system-design-concepts/serving-constrained-resources]] — Scarce/expensive backends (GPUs): a queue smooths bursts but can't fix a deficit; add-capacity / shed / degrade
- [[wiki/system-design-concepts/llm-inference-serving]] — What sets a GPU's serving capacity: KV cache (memory-bound) + continuous batching
- [[wiki/system-design-concepts/async-response-routing]] — Returning an async/streamed result to the caller: session registry + pub/sub back-channel; durable log vs real-time channel
- [[wiki/system-design-concepts/the-log-abstraction]] — The log as system-of-record; every table/index/cache is a replayable projection; O(N²)→O(N) data integration; log+serving split
- [[wiki/system-design-concepts/table-log-duality]] — Table = fold of the changelog; CDC vs materialized view; fault-tolerant stream state; compaction vs retention
- [[wiki/system-design-concepts/lambda-vs-kappa]] — Two pipelines (batch+realtime, merge) vs one (stream+replay); the dashboard-approx/billing-exact split
- [[wiki/system-design-concepts/event-time-vs-processing-time]] — When it happened vs when observed; event-time windowing, watermarks, late-data policy; why a dashboard number mutates
- [[wiki/system-design-concepts/exactly-once-semantics]] — Effectively-once = at-least-once + dedup-by-key + atomic commit; delivery≠effect; Kafka→Kafka boundary

### Theory
- [[wiki/theory/durability-math]] — Deriving nines from disk AFR + RF + MTTR; why MTTR (not RF) is the lever
- [[wiki/theory/consistency-models]] — The spectrum eventual→strict-serializable; why W+R>N is NOT linearizable
- [[wiki/theory/consensus-raft]] — Raft = election + log replication + safety, all on majority overlap; the linearizable mechanism
- [[wiki/theory/consistent-hashing]] — Ring + vnodes for stable partitioning; the copyset durability trade-off of vnode count
- [[wiki/theory/vector-clocks]] — Causal-history counters that detect concurrent writes (siblings); vs Lamport clocks; why leaders skip them
- [[wiki/theory/bloom-filters]] — Probabilistic set membership for space-efficient dedup
- [[wiki/theory/pure-functional-programming]] — Pure functions, immutability, expressions-over-statements
- [[wiki/theory/folds-and-tail-recursion]] — foldl vs foldr, and how TCO turns recursion into a loop
- [[wiki/theory/osi-model]] — The 7-layer model and encapsulation; where TLS and VPNs sit
- [[wiki/theory/durability-rpo-rto]] — RPO/RTO, event sourcing, and the non-idempotent replay hazard on crash recovery
- [[wiki/theory/copy-on-write-vs-mvcc]] — Isolating concurrent views: base+delta overlays (git worktrees) vs. versioned snapshots
- [[wiki/theory/rate-limiting-algorithms]] — The five window shapes (fixed/sliding-log/sliding-counter/token/leaky) and their trade-offs
- [[wiki/theory/actor-model-message-passing]] — Actor isolation via copy (BEAM) vs immutability+pointers (Akka); the >64B shared-heap loophole
- [[wiki/theory/concurrency-primitives]] — Process vs thread vs green thread, ranked by context-switch cost (TLB flush vs mode switch vs user-space)
- [[wiki/theory/latency-numbers]] — The latency ladder (L1→RAM→LAN→disk→WAN); latency stalls while bandwidth doubles; physics-bound vs engineering-bound
- [[wiki/theory/state-machine-replication]] — Deterministic + same ordered input log → identical replicas; the bridge from log to consensus

### Tech
- [[wiki/tech/elm]] — Pure functional front-end language; the Elm Architecture (TEA) state loop
- [[wiki/tech/https-tls]] — HTTPS over TLS: handshakes, cipher suites, PKI, forward secrecy
- [[wiki/tech/vpn]] — Layer-by-layer VPN build; tunneling, IPsec/OpenVPN/WireGuard, defense in depth
- [[wiki/tech/aws-rds-postgresql]] — Managed Postgres: Multi-AZ topologies & node counts, gp3/io2, `synchronous_commit`
- [[wiki/tech/aws-elasticache-redis]] — Managed Redis/Valkey: persistence semantics, node vs serverless, data tiering
- [[wiki/tech/envoy-ratelimit-service]] — The global RLS: gRPC + descriptor tree + Redis; identity-agnostic, edge vs mesh
- [[wiki/tech/istio-service-mesh]] — CRDs→Envoy via istiod; mTLS/SPIFFE authz; EnvoyFilter escape hatch
- [[wiki/tech/kafka]] — Distributed append-only log; acks/ISR durability dial; per-partition ordering; KRaft; tiered storage; Connect/Streams

### Coding Patterns
- [[wiki/coding-patterns/fold-accumulator]] — Reduce a list via a threaded accumulator: naive → tail-recursive → fold

### Behavioral
- [[wiki/behavioral/disagreement-customer-proxy-connectivity]] — Disagreeing with senior architects on customer-proxy connectivity; proved a POC then argued against it; fast-pathed the durable fix

## Statistics
- Total wiki pages: 57
- Total sources: 16
- Last updated: 2026-08-19
