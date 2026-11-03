---
source: interview session (raw transcript not published)
type: doc
date_extracted: 2026-08
topic: system-design-concepts
---

# Design Uber Driver-Allocation — Mock Interview

Distilled from a ~40-min mock systems-design interview: **"Design an Uber-style driver allocation platform"** — rider requests a ride (pickup + drop-off), backend finds nearby available drivers, offers the ride, driver accepts, rider is connected. Scale given: **1B riders, 100M drivers, 20M completed rides/day.** Scheduling-ahead and pricing were out of scope.

## Key Ideas

- **Proximity search is a geospatial-index problem, not a DB-flavour problem.** You can't scan 100M drivers per request. Bucket the world into cells and index `cell → drivers`, so a query touches only the rider's cell + its neighbours. The real design axis is the **cell scheme**: fixed grid (simple, but hotspots), **geohash** (Z-order string prefix = coarser cell — what Redis `GEO`/`GEOSEARCH` uses under the hood), **QuadTree** (adaptive: subdivides dense areas), **S2** (Google, spherical cells on a Hilbert curve), **H3** (Uber's actual choice: hexagons → uniform neighbour distance, no corner ambiguity). Reaching for a **graph DB** here is a tool-fit error: proximity is a 2-D range/radius query, not relationship traversal.
- **The location-update write path is the dominant load, not the match path.** 100M drivers pinging location every ~4s ≈ **~25M writes/s** of `driver → cell` churn — orders of magnitude above the ~230 rides/s match load. It's the heaviest thing in the system. It wants a write-optimized, in-memory, sharded hot store (Redis per zone, or an in-memory geo-index) and only *cell-crossing* updates need to touch the index, not every ping.
- **The hot cell is the true crux.** A stadium letting out, an airport, a surge zone → one cell holds a huge density of riders *and* drivers. A fixed grid turns that into a hotspot: one shard, one partition, melts. This is the **spatial twin of hot-key write contention** ([[wiki/system-design-concepts/hot-key-write-contention]]): aggregate QPS shards trivially, concentration on one cell does not. Mitigations: adaptive subdivision (QuadTree splits the dense cell), per-zone sharding with elastic consumers, and treating the surge cell specially.
- **Dispatch is a two-sided offer protocol, and its correctness lives in the atomic claim.** Matching a driver isn't assignment — the driver must *accept*. Rank candidates (proximity + ETA + rating + accept-likelihood), then either **offer sequentially** (one at a time, short timeout — simple, no double-accept) or **broadcast to top-N** (parallel, first-accept-wins — faster, but now you must prevent double-assignment). First-accept-wins is only correct if acceptance is an **atomic conditional claim** on *two* invariants — one rider gets one driver (`ride: OFFERED→ASSIGNED` via CAS) **and** one driver gets one ride (`driver: FREE→ON_TRIP` via CAS) — done as a single atomic op (Redis Lua / DB txn with unique constraint / fencing token). Losing offers are then cancelled. An **in-process lock** is *not* a fence: requests can land on different instances, and the instance can crash mid-claim.
- **A probabilistic structure is a pre-filter, never the source of truth for a correctness decision.** Using a **Bloom filter** as the authoritative "is this driver available?" check — as if it were "100% accurate" — is wrong. Membership is *"definitely absent"* (certain) xor *"possibly present"* (may be a false positive). It can cheaply **pre-filter** obviously-unavailable drivers to cut load, but the no-double-assignment guarantee must be closed by the atomic claim above. If you do use one, insert the set so that the **certain** direction is the safe one and the **uncertain** direction merely costs a skipped opportunity, not a correctness violation. See [[wiki/theory/bloom-filters]] (anti-pattern section).
- **Absorb spikes with a durable queue; size concurrency with Little's Law.** Fronting the match service with a partitioned ride-request queue (so a crash doesn't drop in-flight requests, and consumers scale on queue depth) absorbs spikes. And `concurrency = arrival × service-time = 20,000/s × 30s = 600,000` outstanding requests is textbook Little's Law.

## My Understanding

- The baseline shape: grid/cells for geo, per-zone Redis sharding, a spike-absorbing partitioned queue, stateless elastic match workers, read replicas, requeue-on-no-match, and Little's Law for outstanding-request sizing.
- **"Per day → per second" is ÷~10^5, not ÷10^4.** 86,400 s/day ≈ 10^5, so 20M rides/day ≈ **~230 rides/s**, not 2,000/s.
- "Once matched, stays matched" is the top NFR, so that is exactly where to check for a probabilistic structure on the correctness path. The authoritative mechanism is a boring atomic CAS on the ride/driver records; a Bloom filter is at best an optimization.
- When the problem is "find nearby X at scale," the hard part is almost never the happy-path lookup — it's the **hotspot** (dense cell / hot key) and the **churn** (update write volume). Surge concentration in one cell, and adaptive vs fixed cells, is the same class of crux as an auction's hot key.
- "The service has a lock" is not a distributed fence. Broadcasting to N drivers in parallel requires an atomic, cross-instance claim mechanism and a loser-cancellation path.
- The ~25M writes/s location-update path deserves first-class design attention, not a footnote.
- Dispatch is a reusable platform primitive: rides, Eats, and couriers all share the same rank → offer → claim loop.

## Open Questions

- **Adaptive vs fixed cells at the hotspot:** concretely, how does a QuadTree (or H3 resolution change) re-shard a surge cell *online* without dropping in-flight location updates or match queries? What triggers the split/merge?
- **Location-update write path:** at ~25M pings/s, what actually absorbs it — client-side dead-reckoning + only-emit-on-cell-cross? A write-behind buffer? What's the freshness SLA on `driver → cell` before matches start missing?
- **Atomic claim mechanism, exactly:** Redis Lua CAS on `{ride_id, driver_id}` vs a DB txn with a unique constraint vs a fencing token — which, and how does the loser-cancellation propagate to the other N-1 drivers' apps over the WebSocket?
- **Match fairness / starvation:** broadcasting to top-5 by proximity can starve lower-rated or farther drivers indefinitely; where does fairness/earnings-balancing live without wrecking rider ETA?
- **Cross-zone edge:** a rider at a zone boundary — how do neighbouring-shard candidate sets get unioned without double-offering the same driver from two shards?

## Connections

- Spatial twin of: [[wiki/system-design-concepts/hot-key-write-contention]] — the hot *cell* is the hot *key* in 2-D; aggregate load shards, concentration doesn't.
- Closes correctness with: [[wiki/system-design-concepts/exactly-once-semantics]] — one-driver-one-ride is an idempotent atomic claim (conditional write keyed by `ride_id`/`driver_id`), the "dedup-by-key + atomic commit" pattern.
- Pattern pages: [[wiki/system-design-concepts/geospatial-indexing]] (proximity search + hot cell) and [[wiki/system-design-concepts/dispatch-and-matching]] (offer protocol + atomic claim).
- Anti-pattern extracted to: [[wiki/theory/bloom-filters]] — probabilistic structure on a correctness path; which direction is certain.
- Spike absorption / elastic consumers reuse: [[wiki/system-design-concepts/the-log-abstraction]] and [[wiki/tech/kafka]] (partitioned ride-request queue, scale on depth); return-path to the rider echoes [[wiki/system-design-concepts/async-response-routing]] (match result comes back asynchronously over the rider's channel).
- Estimation primitives: [[wiki/theory/latency-numbers]]; Little's Law (`concurrency = QPS × latency`) as applied for the 600k outstanding-requests figure.
- Partitioning lens: [[wiki/system-design-concepts/hash-vs-range-partitioning]] — sharding the geo index by zone, and why a hot cell defeats it.

## Concepts flagged for wiki (pending /update-wiki)
- **Geospatial indexing / proximity search** — cell schemes (grid/geohash/QuadTree/S2/H3), read (ring query) vs write (location churn) paths, the hot-cell hotspot, why not a graph DB. *(new)*
- **Dispatch & matching (two-sided offer)** — rank → offer sequential vs broadcast top-N → first-accept-wins → atomic two-invariant claim → timeout/requeue; assignment correctness lives in the claim, not a lock. *(new)*
- **Bloom filter as pre-filter, not authority** — certain-vs-uncertain direction; correctness-critical membership needs an atomic op. *(extend bloom-filters)*
- **Hot cell = hot key in 2-D** — cross-link the spatial hotspot to write-contention. *(extend hot-key-write-contention)*
