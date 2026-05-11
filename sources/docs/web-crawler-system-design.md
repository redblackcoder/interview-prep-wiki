---
source: docs/web-crawler-system-design/
source_url: https://github.com/redblackcoder/interview-prep-raw/blob/main/docs/web-crawler-system-design/
type: doc
date_extracted: 2026-05-11
topic: system-design-concepts
---

# Web Crawler System Design

## Key Ideas
- A web crawler's core challenge is **work distribution without redundancy** — dividing URLs across a fleet so no worker starves, none is overloaded, and no URL is fetched twice.
- The system decomposes into: Discovery (finding URLs), Management (distributing/scheduling work), Fetching (stateless workers), and Storage (dedup + results).
- Consistent hashing by host is the partitioning strategy — assigns host ownership to shards, enabling per-host politeness enforcement and localized DNS caching.
- Bloom filters prevent duplicate URL fetches across the distributed fleet.
- Async job-based API (POST → jobId, GET status, GET results) decouples crawl submission from completion — suits long-running crawls with paginated output.
- Depth-limiting and configurable constraints bound the crawl scope (e.g., max 1000 input URLs, depth 5, producing ~100K results).

## My Understanding
- Both designs (practice and interview) solve the same fundamental problem: distributing crawl work across workers while avoiding redundant fetches and ensuring steady progress.
- The practice design emphasizes infrastructure: sharding by host via consistent hashing, S3 storage partitioned by host, bloom filters for dedup, a Control Plane/Shard Manager for rebalancing, and stateless fetchers with optional headless browser support.
- The Atlassian interview design emphasizes API interfaces and component interaction: a job-based async API, a Worker Manager distributing from a Queue to Fetcher nodes, a relational DB tracking jobs/URLs/images/pages, and handling write-heavy load.
- These two approaches converge — the Worker Manager needs consistent hashing internally to distribute queue work to workers, and the practice design also uses queues (partitioned by host) distributed through a Crawling Manager. Same ideas, different presentation angles.
- The hardest part is ensuring no worker starves or is overloaded — the system must make progress at a steady, optimal pace across the fleet.
- At the crux: work distribution while avoiding redundant work. No duplicate fetches, shared DNS cache, maximize resource utilization (memory and CPU).

## Open Questions
- **Politeness/rate limiting per domain across distributed workers** — how to enforce crawl-delay and robots.txt limits when multiple workers might target the same domain? Consistent hashing helps (host affinity) but what about rebalancing transitions?
- **URL priority and freshness** — how to decide re-crawl order? Priority queues per host? Freshness signals? How does this interact with depth-limiting?
- **Failure handling** — what happens when a worker dies mid-crawl? Need idempotent fetch operations, checkpointing of discovered-but-not-yet-fetched URLs, and lease-based task ownership with timeouts.

## Connections
- Relates to: [[system-design-concepts/consistent-hashing]] — core partitioning strategy for host-to-shard assignment
- Relates to: [[theory/bloom-filters]] — probabilistic dedup of URLs across distributed fleet
- Relates to: [[system-design-concepts/work-distribution]] — the central challenge of crawler design

## Key Quotes / Annotations
From practice design (Web Crawler.excalidraw.md):
> "Discoverer — Shard by host. S3 buckets also shared by host. Since Web is too big, it is unlikely that one host (wikipedia.com) can overwhelm a single shard."
> "Use consistent hashing to distribute hosts across multiple shards, so that rebalancing can be done effectively with minimal content movement."

From Atlassian interview design:
> "API: post(list<url>) -> jobId; status(jobId) -> SUCCESS/PENDING/ERROR; get(jobId) -> Map [Paginated]"
> "DB - Write Heavy Load"
> "ConcurrentMap<Key, Queue<Url>>"
