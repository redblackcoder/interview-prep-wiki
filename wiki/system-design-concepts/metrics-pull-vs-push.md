# Metrics Collection: Pull vs Push

A design lens for the collection tier of a metrics/observability system: does the **monitor scrape** each service (pull, Prometheus), or does each service/agent **send** metrics outward (push, StatsD/OTLP)? The transport looks like the decision, but the load-bearing differences are elsewhere — liveness, reachability, and what representation crosses the wire.

## The one-sentence mental model

> **Pull inverts control: the monitor owns the target list, so a missed scrape is itself the "down" signal. Push hands control to the producer, which buys you ephemeral/cross-boundary reach but forces you to *synthesize* liveness and defend against a firehose.**

## The two models concretely

- **Pull (Prometheus):** each service exposes `/metrics` in OpenMetrics text. A scraper holds the target list — discovered via K8s API / Consul / EC2 tags — and scrapes each on an interval (15–60s typical). Service is passive.
- **Push (StatsD / DogStatsD / OTLP):** the SDK, a sidecar, or a daemon sends metrics to a collector / queue (Kafka) / ingest endpoint; a consumer writes the TSDB. Producer is active.

## Pull — pros / cons

**Pros:** free per-target liveness (`up` metric — down vs idle are *distinguishable*, the single biggest win); natural backpressure (overloaded monitor just scrapes slower — no firehose during an incident); no endpoint/creds on the producer (onboarding = "expose `/metrics`"); `curl`-able for dev/debug; multi-consumer (many Prometheis scrape the same endpoint); scraper stamps trustworthy target labels (pod/node/zone) the producer can't forge.

**Cons:** service discovery is mandatory and hard under churn (autoscaling, spot, FaaS); short-lived/batch jobs don't fit (Pushgateway is the bolt-on patch — an admission pure pull can't cover them); inbound reachability required (NAT / firewall / cross-VPC / SaaS-into-customer-network is painful); resolution capped by scrape interval; every scrape re-serializes the full metric set → load scales with scrapers × cardinality.

## Push — pros / cons

**Pros:** handles ephemeral / event-driven work (Lambda, batch, CI, browser/mobile) the monitor can't reach; crosses NAT/firewalls outbound (why SaaS vendors are push); higher event-level resolution + true per-event semantics; producer controls emission + can buffer across backend outages; discovery-free (producers just show up) → scales to huge heterogeneous fleets.

**Cons:** down-vs-idle ambiguity (must build heartbeats); backpressure / thundering-herd risk — UDP StatsD drops packets *silently* under load, losing data exactly when you need it; endpoint + write creds on every producer (mitigated by a local agent as indirection); harder local debug; aggregation-correctness pitfalls if you push non-mergeable percentiles (see [[system-design-concepts/mergeable-metrics-and-quantiles]]).

## Real-world architectures

- **Prometheus / Kubernetes — pull.** Works because the K8s API *is* the service registry. **Pushgateway** patches batch jobs. For long-term storage, Prometheus then **`remote_write` pushes** into **Cortex / Mimir / Thanos / VictoriaMetrics** — note the hybrid: pull at the edge, push into the durable store.
- **StatsD / DogStatsD (Etsy, Datadog) — push.** UDP fire-and-forget to a local agent; agent aggregates and pushes upstream.
- **Facebook Gorilla/ODS, Uber M3, Netflix Atlas — push.** At fleet scale, central scraping of millions of ephemeral hosts is infeasible; producers push into an aggregation tier.
- **AWS CloudWatch — push** (`PutMetricData`), a multi-tenant SaaS that can't reach into customer VPCs.
- **OpenTelemetry — push by default (OTLP)** but the Collector has a Prometheus *receiver* (pull) and *exporter* (expose for scrape). The Collector is the universal seam: **scrape one side, push the other.**

## When to use which

**Use pull when:** strong service discovery + controllable reachability (K8s/Consul); targets are long-lived; you value liveness detection and `curl` debuggability; multiple independent consumers; bounded cardinality and a modest interval.

**Use push when:** ephemeral/event-driven workloads; producer unreachable inbound (NAT/firewall/SaaS); you need per-event granularity/sub-scrape resolution (with mergeable aggregates); huge/heterogeneous fleet where a central target list is impractical; you already have a durable queue for buffering/replay/backpressure.

**On a 1s scrape:** aggressive — you lose pull's low-overhead advantage (full re-serialization every second × scrapers × cardinality) and store near-duplicate samples. If you genuinely need 1s resolution, that's a signal you're in push territory (local agent aggregating per-event and pushing). If "1s" just means "fresh," 10–15s pull is the pragmatic default.

## Key points

- The debate is usually mis-framed as transport. The real forks: **liveness signal** (pull free / push synthesized), **reachability + discovery**, **mergeable vs non-mergeable representation**, **exact vs sampled counting**. Transport correlates with these but doesn't cause them.
- Production is **hybrid**: pull at the instrumented edge where discovery/reachability are solid; push across trust/network boundaries and for ephemeral work; an OTel-Collector-style agent as the seam; **push into the long-term store almost universally** (`remote_write`).
- Failure mode under load is the tell: pull degrades to *slower scrapes* (safe); naive UDP push degrades to *silent drops* (dangerous, correlated with incidents).

## Interview angle

> "I don't pick pull or push dogmatically. I place the boundary on constraints. Pull where I own the network and have good discovery — Kubernetes, where the API server is the registry — because a missed scrape is my liveness signal and I get natural backpressure. Push across NAT/firewall boundaries, for ephemeral or event-driven work Prometheus can't reach, and where I need per-event resolution. In practice it's hybrid: scrape at the edge, then `remote_write` push into Mimir/Thanos for durable storage, with an OTel Collector as the seam. And I'm careful that whatever I push for latency is a *mergeable* structure — bucket counts or a sketch — never a per-host percentile."

## Connections
- [[system-design-concepts/red-metrics-exposition]] — how Requests/Errors/Duration are actually exposed and why a 60s scrape stays accurate at 50k QPS
- [[system-design-concepts/mergeable-metrics-and-quantiles]] — the aggregation-correctness pitfall that bites push (and pull) if you ship percentiles instead of buckets/sketches
- [[system-design-concepts/counter-reset-and-restart-recovery]] — how each model survives process/agent restarts
- [[system-design-concepts/the-log-abstraction]] — the durable queue (Kafka) that fronts a push pipeline for buffering/replay
- [[system-design-concepts/global-rate-limiting]] — the same "central-scrape vs local-emit" tension appears in distributed counting
- [[tech/kafka]] — the ingestion buffer that absorbs push backpressure
- [[theory/latency-numbers]] — cross-node RTT is why aggregation is pushed to a local agent before it crosses the network
- [[system-design-concepts/sidecar-vs-daemonset]] — where the local agent/scrape-target actually runs (one-per-pod vs one-per-node)
- [[system-design-concepts/local-ipc-transports]] — the app→agent hop for a push model: UDP/UDS load-shedding vs HTTP backpressure
- [[system-design-concepts/edge-shed-vs-core-durability]] — push chooses where to shed (edge) and where to persist (core queue); pull sheds implicitly by scrape interval
- [[system-design-concepts/queue-placement]] — for a push pipeline: whether/where the ingestion queue sits relative to the collector

## Sources
- [[sources/docs/metrics-collection-observability-self-study]] — §1 pull-vs-push, pros/cons, real architectures, when-to-use
</content>
