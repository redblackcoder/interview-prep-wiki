# Web Crawler

A system that systematically fetches web pages starting from seed URLs, discovers new URLs from fetched content, and stores/processes the results. The core challenge is not fetching itself — it's coordinating a distributed fleet to crawl efficiently without redundancy, starvation, or violating politeness constraints.

## Architecture

```
Seed URLs → Discovery Service → URL Queue (partitioned by host)
                                       ↓
                              Crawl Manager / Shard Manager
                                       ↓
                              Fetcher Workers (stateless)
                                       ↓
                              Storage (S3 / DB) + New URL extraction → back to queue
```

### Components
- **Discovery Service**: Expands seed URLs by parsing fetched pages for new links. Feeds the URL frontier.
- **Crawl Manager**: Distributes work from partitioned queues to workers. Enforces robots.txt. Prevents overload on any single worker.
- **Fetcher Workers**: Stateless nodes that fetch page content. Optionally render JS via headless browser. Report discovered URLs back to the queue.
- **Storage**: S3 buckets (partitioned by host) for page content. Relational DB for job/URL/result tracking.
- **Bloom Filter**: Distributed dedup — prevents re-fetching URLs already in the corpus.
- **Control Plane / Shard Manager**: Handles rebalancing when workers join or leave.

## How it works

1. Partition the URL space by host using [[theory/consistent-hashing]].
2. Each shard owns a set of hosts — all URLs under those hosts route to the same partition.
3. Workers pull batches from their assigned partition's queue.
4. On fetch: extract content + new URLs. New URLs are checked against a [[theory/bloom-filters|Bloom filter]] for dedup, then routed to the correct partition.
5. Crawl Manager throttles per-host to respect politeness (crawl-delay, robots.txt).

## Key points
- **Host-based sharding** is the natural partition key — it co-locates politeness enforcement, DNS caching, and storage.
- **Stateless workers** enable horizontal scaling. All state lives in the queue + storage layer.
- **Write-heavy workload** — each fetch produces many new URLs and stored pages. Design storage for write throughput.
- **Async job API** decouples submission from completion: POST(urls) → jobId, GET status(jobId), GET results(jobId) with pagination.
- **Depth limiting** bounds crawl scope (e.g., max depth 5 from seed). Essential for interview scoping and real-world resource management.

## Scaling the fetch (I/O-bound throughput)

A crawler's per-worker limit is **not CPU or thread count — it's in-flight network I/O.** A fetch spends almost all its time parked waiting on a remote server, so "10k docs/s ⇒ 10k threads/machines" is the wrong model.

- **Event loop, not thread-per-fetch.** One worker drives thousands of concurrent fetches on an async event loop; the kernel owns the sockets and wakes the loop when bytes arrive. Offload the CPU-bound HTML parse to a **separate thread pool** so it never blocks the loop.
- **Connection count stays low via host co-location.** N docs/s does *not* mean N open sockets. If a worker owns a set of **hosts**, its requests concentrate on few servers → the kernel **reuses TCP connections** and **HTTP/2 multiplexes** many requests over one (no repeated handshake).
- **HTTP/2 head-of-line blocking → QUIC/HTTP-3.** One slow response stalls every request sharing an HTTP/2 connection (TCP-level HOL). QUIC's independent streams remove transport-level HOL — prefer it where the origin supports it.
- **Bandwidth is bits, not bytes.** 1000 docs/s × 10 KB = 10 **MB/s = 80 Mbps** per worker (the ×8 byte→bit step is the classic slip). Well under a 10–25 Gbps NIC; a rack of ~50 such workers is ~4 Gbps aggregate — network is rarely the fetch bottleneck.
- **Storage egress:** write content to **local disk first, drain to object store** (S3 multipart / concurrent uploads) so the fetch path isn't coupled to object-store latency.

## The dedup/enqueue seam (don't lose work on crash)

Distributed dedup hides a correctness seam that's easy to miss: the **order of the "mark seen" and "enqueue" writes**.

- **Mark-seen-*then*-enqueue loses work.** If a worker marks a URL in the seen-set and crashes before enqueuing it, the URL is now permanently deduped-away but **never crawled**. Reverse it: **enqueue first, then mark seen**, and only **commit the input offset after both** the content is durably stored *and* child URLs are enqueued. At-least-once + idempotent dedup then makes replay safe (worst case: re-fetch, never lose).
- **"Batch add is atomic" ≠ "I know which were new."** A batched set-add returns a *count*, not *which* members were novel — and in a sharded store the batch spans slots, so there's no single atomic multi-key op. Use **per-key check-and-adds** (pipelined, or a small Lua script); each returns 0/1 and **1 = novel = the enqueue signal**.
- **The seen-set doesn't fit in RAM at web scale.** Billions of URLs → multi-TB. Put a [[theory/bloom-filters|Bloom filter]] (or SSD-backed set) in front of an authoritative on-disk set; keep only hot keys in memory. Hazard: a Bloom false-positive means a real page is **never** crawled.

## Partition by host: one key, three properties

The natural shard key is **host**, and choosing it once buys three things at no extra cost: **dedup locality**, **connection reuse** (the fetch-throughput win above), and **per-host politeness / rate-limiting**. Partitioning by *URL* instead fragments a host across workers and defeats connection reuse — a subtle incoherence when dedup and fetch pick different keys. Assign hosts to shards with [[theory/consistent-hashing]].

## Recrawl: priority classes vs. scheduling

Two different needs are easy to conflate:
- **Priority classes** (real-time vs. daily vs. weekly) → separate queues / consumer pools so a slow bulk tier can't starve urgent crawls. A log ([[tech/kafka]]) is the right substrate here.
- **Time-delayed recrawl** ("fetch this again next Tuesday") → **not** a job you can park in a log. Use a **scheduler**: a store keyed by `next_crawl_time` (e.g. a Redis sorted set) + a due-poller that enqueues when a URL comes due. Set cadence from observed change-rate, and use **conditional GET** (`ETag` / `If-Modified-Since`) + a **content hash** to skip unchanged pages cheaply.

## Interview angle

> "A web crawler's core problem is work distribution without redundancy. You partition by host using consistent hashing, assign partitions to workers, dedup with bloom filters, and enforce per-host politeness at the partition level. The worker fleet is stateless — all coordination happens through partitioned queues and a crawl manager."

## Connections
- [[system-design-concepts/work-distribution]] — the central challenge: distributing crawl work across a fleet without starvation or duplication; the host-vs-URL partition-key choice is the crux of that pattern too
- [[theory/consistent-hashing]] — partitioning strategy for host-to-shard assignment (dedup + connection reuse + politeness from one key)
- [[theory/bloom-filters]] — probabilistic URL dedup across the distributed fleet; the in-front tier for a RAM-bound seen-set (FP ⇒ never crawled)
- [[system-design-concepts/exactly-once-semantics]] — the dedup/enqueue ordering seam: enqueue-before-mark + commit-offset-after-durable-write make crash replay safe
- [[tech/kafka]] — the frontier as a log; priority-class topics vs. why a log isn't a delay-queue for recrawl
- [[theory/durability-rpo-rto]] — RPO-for-throughput on the seen-set; the non-idempotent-replay hazard on crash recovery

## Sources
- [[sources/docs/web-crawler-system-design]] — practice design + Atlassian interview design (the #1 baseline)
- [[sources/docs/design-web-crawler-scaling-self-interview]] — scaling-focused re-attempt (self-graded): I/O-bound throughput, connection reuse, the dedup/enqueue seam, recrawl scheduling
- [[sources/docs/networking-deep-dive]] — TCP reuse, HTTP/2 multiplexing + head-of-line blocking, QUIC (the fetch-throughput argument)
