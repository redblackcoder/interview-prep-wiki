# Read-Side Fan-Out (Live Updates at Scale)

The mirror image of [[system-design-concepts/hot-key-write-contention|hot-key *write* contention]]: one entity (an auction, a live-match score, a stock tick) is **watched by a huge audience**, and every change must reach all of them *live*. The write problem is many→one (serialize the writers); this is **one→many** (broadcast one update to millions of readers). It's the piece the auction design left open: showing the ticking price to everyone watching without melting the store.

## The one-sentence mental model

> **Push, don't poll — but never push *every event to every socket*. Coalesce the update to its latest value, fan it out through a cross-node bus (each server subscribes once, then pushes to its own sockets), and bootstrap new viewers with a snapshot + subscribe-then-reconcile.**

## Why polling fails and naïve push also fails

- **Polling** millions of watchers against Redis for one hot auction recreates a read hot-key: O(readers) reads/sec on one key, plus a thundering herd in the final seconds. Don't.
- **Naïve push** ("broadcast every bid to every watcher") is a **fan-out amplification** bomb: 10k bids/s × 1M watchers = ~10¹⁰ messages/s. The fan-out is the cost, and it peaks on exactly the auctions you care about.

## The three moves that make it tractable

**1. Coalesce a monotonic/aggregate value to "latest," push at a bounded rate.** The live price is a **running `max`** — intermediate bids are irrelevant for display, so publish "current max = $X" at ~4–10 Hz, not once per bid. Two payoffs: throughput drops from *per-bid* to *per-tick*, and **delivery can be lossy** — a dropped update is corrected by the next tick (monotonic + idempotent, same algebra as [[system-design-concepts/commutative-aggregation]]). Note this partly retires client-side max computation: if you push the already-folded value, the client just renders it.

**2. Two-level fan-out over a cross-node bus.** Watcher sockets for one auction are spread across *many* WebSocket servers. A bid ingested on node A must reach every node holding a relevant socket. The pattern: each server **subscribes once** to a channel (`auction:{id}`) on a **pub/sub bus**; the publisher publishes **once**; the bus fans out to subscribing *servers*; each server pushes to its *local* sockets. Fan-out becomes bus→N servers (small) then server→sockets (local), instead of publisher→millions. This is the [[system-design-concepts/message-fanout|message-fanout]] machinery; the bus is typically Redis Pub/Sub (ephemeral, lossy-OK), Redis Streams, or Kafka (durable, replayable, heavier).

**3. Connection tier + bootstrap.** Millions of WebSockets ≈ tens of edge boxes at ~100k conns each (Little's law), with a session registry, reconnection, and backpressure. A joining client races the stream: read snapshot from Redis = `$X`, but bids between the read and the subscribe are missed. Fix: **subscribe first, buffer, then snapshot, then reconcile** — and because the value is a monotonic max, reconciliation is just `display = max(snapshot, buffered)`. Idempotence makes over/under-counting impossible.

## Key points
- **The authoritative value stays server-side.** Client-side or per-node folds are display hints only — clients see different subsets and can't be trusted (integrity). The winner is still computed from the durable log at close.
- **Choose the bus by delivery needs.** Ephemeral live display where loss is self-healing → **Redis Pub/Sub** (fire-and-forget, at-most-once, no persistence, dead simple). Need replay / late-join history / durability → **Redis Streams** or **Kafka**. Don't pay for durability on a stream you've already made loss-tolerant by coalescing.
- **Redis Pub/Sub across a cluster:** classic `PUBLISH` broadcasts to *all* cluster nodes (bus becomes the bottleneck); **sharded pub/sub** (`SPUBLISH`/`SSUBSCRIBE`, Redis 7.0+) confines a channel to its shard so the bus scales horizontally.
- **This is a distinct problem from routing a result to one caller** ([[system-design-concepts/async-response-routing]]): that's one→one (session registry keyed by caller); this is one→many broadcast.

## Interview angle

> "Showing a live price to millions of watchers is the read-side mirror of the write hot-key. I don't poll and I don't push every bid to every socket — that's a fan-out bomb. The price is a monotonic max, so I coalesce to the latest value and push at a bounded rate, which also lets delivery be lossy since the next tick heals a drop. Sockets are spread across many servers, so I put a pub/sub bus in between: each server subscribes once to `auction:{id}`, the publisher publishes once, the bus fans out to servers, each server pushes to its local sockets. New viewers bootstrap by subscribing first, then reading the Redis snapshot, then reconciling with `max()`. Redis Pub/Sub is perfect here because loss is self-healing; if I needed replay I'd use Streams or Kafka. The authoritative winner is still computed server-side from the log — the pushed value is only a display hint."

## Connections
- [[system-design-concepts/hot-key-write-contention]] — the write-side twin; this page is its read-side mirror (one→many vs many→one)
- [[system-design-concepts/commutative-aggregation]] — monotonic/idempotent `max` is what lets you coalesce, tolerate loss, and reconcile a late-joiner with `max()`
- [[system-design-concepts/message-fanout]] — the subscribe-once-per-node, push-to-local-sockets machinery (Discord's Manifold/back-pressure)
- [[system-design-concepts/async-response-routing]] — the one→one sibling: routing a streamed result back to the *specific* caller via a session registry
- [[system-design-concepts/the-log-abstraction]] — Kafka-as-bus option; and the authoritative value is a fold of the log regardless of the live path
- [[tech/aws-elasticache-redis]] — Redis Pub/Sub vs Streams semantics; sharded pub/sub in Cluster mode
- [[theory/latency-numbers]] — the connection-tier sizing (conns/box) and why push beats poll for a few-hundred-ms budget

## Sources
- [[sources/docs/design-instagram-auction-mock-interview]] — the read-fan-out thread: pushing the live max to watchers, bootstrap race, bus choice
- [design-instagram-auction-mock-interview.md](https://github.com/redblackcoder/interview-prep-raw/blob/master/docs/design-instagram-auction-mock-interview.md) — full mock-interview transcript
- [[sources/articles/discord-scaling-elixir-5m-concurrent]] — real-world one→many fan-out at 5M concurrent (Elixir, but the pattern is bus-agnostic)
