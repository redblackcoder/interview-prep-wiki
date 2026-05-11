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

## Interview angle

> "A web crawler's core problem is work distribution without redundancy. You partition by host using consistent hashing, assign partitions to workers, dedup with bloom filters, and enforce per-host politeness at the partition level. The worker fleet is stateless — all coordination happens through partitioned queues and a crawl manager."

## Connections
- [[system-design-concepts/work-distribution]] — the central challenge: distributing crawl work across a fleet without starvation or duplication
- [[theory/consistent-hashing]] — partitioning strategy for host-to-shard assignment
- [[theory/bloom-filters]] — probabilistic URL dedup across the distributed fleet

## Sources
- [[sources/docs/web-crawler-system-design]] — practice design + Atlassian interview design
