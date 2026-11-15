# Interview Prep — Index

## Categories

- [Problems](problems/README.md) — Coding exercises and system designs with worked solutions

- [Theory](theory) — CS fundamentals
- [System Design Concepts](system-design-concepts) — Patterns from design problems
- [Tech](tech) — Technologies and platforms
- [Algorithms](algorithms) — CS algorithms
- [Coding Patterns](coding-patterns) — Patterns from coding problems

## Pages

### System Design Concepts
- [web crawler](system-design-concepts/web-crawler.md) — Distributed web crawler architecture and design trade-offs
- [work distribution](system-design-concepts/work-distribution.md) — Partitioning work across a fleet without starvation or redundancy
- [preemption economics](system-design-concepts/preemption-economics.md) — Why distributed schedulers queue/scale/kill instead of context-switching
- [compressible vs incompressible resources](system-design-concepts/compressible-vs-incompressible-resources.md) — Why k8s overcommits CPU but hard-reserves memory (requests vs limits)
- [network security layers](system-design-concepts/network-security-layers.md) — Payload vs envelope: why HTTPS isn't access control, with a concrete attack
- [zero trust ztna](system-design-concepts/zero-trust-ztna.md) — Beyond perimeter VPNs: per-request, per-app authorization
- [context assembly retrieval ladder](system-design-concepts/context-assembly-retrieval-ladder.md) — Projecting a huge corpus into a bounded context window via a cost-tiered retrieval ladder
- [agent tool sandboxing](system-design-concepts/agent-tool-sandboxing.md) — Permission ≠ isolation; model judgment is never a security boundary; containment over policy
- [agent loop](system-design-concepts/agent-loop.md) — The LLM agent as a reactive state machine: typed blocks, tool_use pairing, verification as tool calls
- [rds vs key value store](system-design-concepts/rds-vs-key-value-store.md) — Relational vs KV as the primary store: durable SSD bytes vs fast DRAM bytes
- [cloud database cost model](system-design-concepts/cloud-database-cost-model.md) — The four cost axes (instance/storage/IO/requests) and the ~100–250× RAM-vs-SSD gap
- [rate limiting](system-design-concepts/rate-limiting.md) — Counter location × window shape × failure trade-off; local vs global, L4 vs L7
- [global rate limiting](system-design-concepts/global-rate-limiting.md) — Multi-region counting: consistent vs accurate, CRDT/home-region, why VPN-hopping doesn't bypass
- [client identification](system-design-concepts/client-identification.md) — What to key a limiter on: authenticated credential vs anonymous IP/ASN/JA4; CGNAT and the IPv6 /64 trap
- [message fanout](system-design-concepts/message-fanout.md) — Pub/sub fan-out without a hot process: Manifold + FastGlobal + semaphore back-pressure
- [read state watermarking](system-design-concepts/read-state-watermarking.md) — Durable chat delivery (commit-before-ACK) + per-user watermark for multi-device notification dedup
- [hash vs range partitioning](system-design-concepts/hash-vs-range-partitioning.md) — Uniform load vs cheap range scans; the DynamoDB composite-key resolution and the hot-partition trap
- [hinted handoff](system-design-concepts/hinted-handoff.md) — Sloppy quorum: stay writable through AZ+1 at RF=3 without raising the replication factor
- [read repair](system-design-concepts/read-repair.md) — Read-time convergence: heal the hot set for free on divergent quorum reads
- [anti entropy merkle trees](system-design-concepts/anti-entropy-merkle-trees.md) — Background convergence for cold keys; Merkle diffing in O(diffs·log N), not O(N)
- [per shard raft](system-design-concepts/per-shard-raft.md) — Linearizable AND scalable: one Raft group per shard, RF=5 for AZ+1 majority
- [leaderless vs leader based](system-design-concepts/leaderless-vs-leader-based.md) — The write-path fork; the Dynamo 2007→DynamoDB 2012 switch and why
- [distributed id generation](system-design-concepts/distributed-id-generation.md) — Global unique IDs with no per-ID coordination: uniqueness by construction, library-not-service, clock-backward + worker-id split-brain
- [serving constrained resources](system-design-concepts/serving-constrained-resources.md) — Scarce/expensive backends (GPUs): a queue smooths bursts but can't fix a deficit; add-capacity / shed / degrade
- [llm inference serving](system-design-concepts/llm-inference-serving.md) — What sets a GPU's serving capacity: KV cache (memory-bound) + continuous batching
- [async response routing](system-design-concepts/async-response-routing.md) — Returning an async/streamed result to the caller: session registry + pub/sub back-channel; durable log vs real-time channel
- [the log abstraction](system-design-concepts/the-log-abstraction.md) — The log as system-of-record; every table/index/cache is a replayable projection; O(N²)→O(N) data integration; log+serving split
- [table log duality](system-design-concepts/table-log-duality.md) — Table = fold of the changelog; CDC vs materialized view; fault-tolerant stream state; compaction vs retention
- [lambda vs kappa](system-design-concepts/lambda-vs-kappa.md) — Two pipelines (batch+realtime, merge) vs one (stream+replay); the dashboard-approx/billing-exact split
- [event time vs processing time](system-design-concepts/event-time-vs-processing-time.md) — When it happened vs when observed; event-time windowing, watermarks, late-data policy; why a dashboard number mutates
- [exactly once semantics](system-design-concepts/exactly-once-semantics.md) — Effectively-once = at-least-once + dedup-by-key + atomic commit; delivery≠effect; Kafka→Kafka boundary
- [hot key write contention](system-design-concepts/hot-key-write-contention.md) — Aggregate load shards for free; contention on one key doesn't; remove/concentrate/approximate the coordination
- [commutative aggregation](system-design-concepts/commutative-aggregation.md) — Order-free ops (max/sum/set-union) need durability + a cutoff, not linearizability; CALM/CRDT; idempotence makes at-least-once safe
- [read side fanout](system-design-concepts/read-side-fanout.md) — One→many live updates: coalesce to latest, two-level fan-out over a pub/sub bus, snapshot+reconcile bootstrap
- [geospatial indexing](system-design-concepts/geospatial-indexing.md) — Proximity search via cell schemes (grid/geohash/QuadTree/S2/H3); the location-write path and the hot-cell crux; not a graph-DB problem
- [space filling curves](system-design-concepts/space-filling-curves.md) — Z-order/Hilbert map 2-D→1-D so a B-tree indexes space; geohash=Z-order; S2's quadtree nests exactly vs H3's aperture-7 hexagons only approximately; lakehouse data-skipping
- [dispatch and matching](system-design-concepts/dispatch-and-matching.md) — Two-sided offer protocol: rank→broadcast top-N→first-accept-wins behind an atomic two-invariant claim; the lock-isn't-a-fence trap
- [timeline fanout hybrid](system-design-concepts/timeline-fanout-hybrid.md) — Social feed build: push (cheap reads) vs pull (cheap writes) vs hybrid; celebrity breaks push, the freshness SLA sets the threshold
- [feed cursor stability](system-design-concepts/feed-cursor-stability.md) — Snapshot-isolated pagination: why offset corrupts a mutating feed; snapshot ID + COW-on-change + TTL
- [video delivery read path](system-design-concepts/video-delivery-read-path.md) — Client prefetch + adaptive bitrate + segmentation; the <100ms budget is on the client; segment-per-S3-object trade-off
- [cache stampede](system-design-concepts/cache-stampede.md) — Thundering herd on a cold/expired hot key: request coalescing, stale-while-revalidate, shield, TTL jitter, pre-warm
- [cache eviction policies](system-design-concepts/cache-eviction-policies.md) — Which entry to drop: exact vs approximate LRU, Redis sampling, CLOCK, LFU, W-TinyLFU; the eviction/concurrency/memory axes
- [metrics pull vs push](system-design-concepts/metrics-pull-vs-push.md) — Scrape vs send: liveness/reachability/mergeability/exactness are the real forks; Prometheus vs StatsD/OTLP; hybrid edge-pull → remote_write-push
- [red metrics exposition](system-design-concepts/red-metrics-exposition.md) — How RED is exposed; aggregation at event time means a 60s scrape is exact at 50k QPS; histogram→p99 math; StatsD/UDP sidecar + sampling variance
- [mergeable metrics and quantiles](system-design-concepts/mergeable-metrics-and-quantiles.md) — Never average p99s; aggregate-then-quantile; cumulative buckets vs DDSketch/native histograms; the observability face of commutative aggregation
- [counter reset and restart recovery](system-design-concepts/counter-reset-and-restart-recovery.md) — rate() reset correction and its climb-back blind spot; delta-push silent gaps; why per-window percentiles are unrecoverable and tail-biased
- [edge shed vs core durability](system-design-concepts/edge-shed-vs-core-durability.md) — reliability increases inward; shed cheap high-rate events at the edge (UDP), persist precious aggregated messages at the core (queue); the flip condition is exactness, not scale
- [sidecar vs daemonset](system-design-concepts/sidecar-vs-daemonset.md) — agent topology decided by upgradeability + blast radius (not runtime memory); one-per-pod isolated-but-version-pinned vs one-per-node central-but-shared
- [local ipc transports](system-design-concepts/local-ipc-transports.md) — app→agent hop: HTTP loopback vs UDS vs UDP; the real axis is backpressure vs load-shedding; a UDS lets you choose
- [queue placement](system-design-concepts/queue-placement.md) — whether to queue (durability + fan-out, not "buffering") and where (behind a thin collector, in front of the bottleneck DB); guard the bottleneck
- [distributed job scheduler](system-design-concepts/distributed-job-scheduler.md) — multi-tenant cron/ad-hoc scheduler: materialize ahead, CAS-claim, lease-run in a sandbox; two-step SKIP LOCKED claim, per-driver leases + re-adoption, sharded Aurora baseline; 40k rows/s in ~370 tx/s
- [lease and fencing](system-design-concepts/lease-and-fencing.md) — ownership as a conditional write; fence token on every transition; per-driver granularity + re-adoption; nested-lease timeline; lease > failover; self-kill on lost lease
- [postgres as a work queue](system-design-concepts/postgres-as-a-work-queue.md) — SKIP LOCKED batch claims; never hold a tx across app logic → two-step claim + fenced assign; dead tuples under long tx are the real risk
- [database write capacity](system-design-concepts/database-write-capacity.md) — size rows/s, tx/s, IOPS, memory, disk separately; batching cuts commits not rows; DynamoDB WCU + per-key wall; pick by shape, not just load
- [transactional outbox](system-design-concepts/transactional-outbox.md) — sync DB → cache/queue without 2PC; sweeper against the source of truth; cache loss costs latency, not correctness
- [fair share scheduling](system-design-concepts/fair-share-scheduling.md) — hot tenant is a capacity-fairness problem: per-tenant quotas + WFQ at dispatch; split only hot tenants
- [distributed file system](system-design-concepts/distributed-file-system.md) — extent-based blob storage: metadata decides where, clients move bytes; (storage file, offset, length) at 4 KB alignment in append-only storage files; range-partitioned namespace; ~100 nodes for 10 PB × 3
- [immutable object commit](system-design-concepts/immutable-object-commit.md) — PENDING → COMMITTED in one conditional write; idempotent commit by upload_id; TTL sweeper; metadata-driven GC never races a slow upload
- [replica repair and re replication](system-design-concepts/replica-repair-and-re-replication.md) — suspect fast, repair patiently; reverse index; fewest-copies-first, throttled, fenced swap; MTTR set by scatter width (300 TB / 100 peers ≈ 4 h)
- [copysets](system-design-concepts/copysets.md) — random per-object placement makes any 3-node loss fatal; constrained rack-diverse copysets cut it to ~0.5% at scatter width 50, trading MTTR
- [replication vs erasure coding](system-design-concepts/replication-vs-erasure-coding.md) — 3x vs RS(6,3) 1.5x; EC repair reads k; replicate hot/open, encode sealed/cold; LRC cuts repair I/O

### Theory
- [durability math](theory/durability-math.md) — Deriving nines from disk AFR + RF + MTTR; why MTTR (not RF) is the lever
- [littles law](theory/littles-law.md) — L = λW; concurrency = rate × duration; per-running-item work scales with L
- [consistency models](theory/consistency-models.md) — The spectrum eventual→strict-serializable; why W+R>N is NOT linearizable
- [consensus raft](theory/consensus-raft.md) — Raft = election + log replication + safety, all on majority overlap; the linearizable mechanism
- [consistent hashing](theory/consistent-hashing.md) — Ring + vnodes for stable partitioning; the copyset durability trade-off of vnode count
- [vector clocks](theory/vector-clocks.md) — Causal-history counters that detect concurrent writes (siblings); vs Lamport clocks; why leaders skip them
- [bloom filters](theory/bloom-filters.md) — Probabilistic set membership for space-efficient dedup
- [pure functional programming](theory/pure-functional-programming.md) — Pure functions, immutability, expressions-over-statements
- [folds and tail recursion](theory/folds-and-tail-recursion.md) — foldl vs foldr, and how TCO turns recursion into a loop
- [osi model](theory/osi-model.md) — The 7-layer model and encapsulation; where TLS and VPNs sit
- [durability rpo rto](theory/durability-rpo-rto.md) — RPO/RTO, event sourcing, and the non-idempotent replay hazard on crash recovery
- [copy on write vs mvcc](theory/copy-on-write-vs-mvcc.md) — Isolating concurrent views: base+delta overlays (git worktrees) vs. versioned snapshots
- [rate limiting algorithms](theory/rate-limiting-algorithms.md) — The five window shapes (fixed/sliding-log/sliding-counter/token/leaky) and their trade-offs
- [actor model message passing](theory/actor-model-message-passing.md) — Actor isolation via copy (BEAM) vs immutability+pointers (Akka); the >64B shared-heap loophole
- [concurrency primitives](theory/concurrency-primitives.md) — Process vs thread vs green thread, ranked by context-switch cost (TLB flush vs mode switch vs user-space)
- [concurrency constructs](theory/concurrency-constructs.md) — The synchronization toolkit: visibility/mutex/atomics-CAS/semaphore/coordination, one construct per race
- [latency numbers](theory/latency-numbers.md) — The latency ladder (L1→RAM→LAN→disk→WAN); latency stalls while bandwidth doubles; physics-bound vs engineering-bound
- [state machine replication](theory/state-machine-replication.md) — Deterministic + same ordered input log → identical replicas; the bridge from log to consensus

### Tech
- [elm](tech/elm.md) — Pure functional front-end language; the Elm Architecture (TEA) state loop
- [https tls](tech/https-tls.md) — HTTPS over TLS: handshakes, cipher suites, PKI, forward secrecy
- [vpn](tech/vpn.md) — Layer-by-layer VPN build; tunneling, IPsec/OpenVPN/WireGuard, defense in depth
- [aws rds postgresql](tech/aws-rds-postgresql.md) — Managed Postgres: Multi-AZ topologies & node counts, gp3/io2, `synchronous_commit`
- [aws elasticache redis](tech/aws-elasticache-redis.md) — Managed Redis/Valkey: persistence semantics, node vs serverless, data tiering
- [envoy ratelimit service](tech/envoy-ratelimit-service.md) — The global RLS: gRPC + descriptor tree + Redis; identity-agnostic, edge vs mesh
- [istio service mesh](tech/istio-service-mesh.md) — CRDs→Envoy via istiod; mTLS/SPIFFE authz; EnvoyFilter escape hatch
- [kafka](tech/kafka.md) — Distributed append-only log; acks/ISR durability dial; per-partition ordering; KRaft; tiered storage; Connect/Streams
- [cdn](tech/cdn.md) — Distributed reverse-proxy cache + routing; edge→shield→origin; immutable+segmented URLs for hit ratio; viral is the easy case

### Algorithms
- [knapsack variants](algorithms/knapsack-variants.md) — 0/1 vs bounded vs unbounded: same O(nW) table; loop direction is the 0/1↔unbounded flip; binary-split / monotonic-deque for bounded

### Coding Patterns
- [fold accumulator](coding-patterns/fold-accumulator.md) — Reduce a list via a threaded accumulator: naive → tail-recursive → fold
- [keyed serial executor](coding-patterns/keyed-serial-executor.md) — Bounded concurrent executor: per-key serial queue + fixed pool + CAS admission + completion hand-off (complete code)
- [concurrent lru cache](coding-patterns/concurrent-lru-cache.md) — Thread-safe LRU ladder: single mutex (get-is-a-writer) → sharding → lock-free-read CLOCK; with code
- [exactly k window counting](coding-patterns/exactly-k-window-counting.md) — Exactly-k subarray counts: single-pass range of valid starts [lo, hi] vs atMost(k) − atMost(k−1); needs a monotone measure (LC 992, 3306)
- [sweep line resource scheduling](coding-patterns/sweep-line-resource-scheduling.md) — Min resources = peak overlap via +1/−1 events or end-time heap; multi-station pools + repositioning greedy; min path cover in general

## Statistics
- Total wiki pages: 99
- Total sources: 27
- Last updated: 2026-11-02
