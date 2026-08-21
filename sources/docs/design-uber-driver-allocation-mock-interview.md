---
source: docs/mock-uber-system-design/Transcript.md
source_url: https://github.com/redblackcoder/interview-prep-raw/blob/master/docs/mock-uber-system-design/Transcript.md
whiteboard_url: https://github.com/redblackcoder/interview-prep-raw/blob/master/docs/mock-uber-system-design/white-board.md
type: doc
date_extracted: 2026-08-20
topic: system-design-concepts
---

# Design Uber Driver-Allocation — Mock Interview

Distilled from an interviewing.io ~40-min systems-design screen: **"Design an Uber-style driver allocation platform"** — rider requests a ride (pickup + drop-off), backend finds nearby available drivers, offers the ride, driver accepts, rider is connected. Scale given: **1B riders, 100M drivers, 20M completed rides/day.** Scheduling-ahead and pricing were out of scope. Interviewer's stated verdict: **"E5.5, not yet E6"** — solid core, wanted more "scope-up / product-extension" thinking.

> **Calibration note (why this extract reads differently):** the interviewer self-described as Meta Senior Staff but the interview was **low-signal** — the technical feedback was thin and partly misdirected, and both parties missed the problem's real crux (geo-hotspots) and its real seam (atomic assignment). This extract therefore leans on **my own** post-hoc analysis, not the interviewer's, and the correct versions of the two mechanisms below are things *neither* of us drove in the room. See **On the interview quality** for the calibration lesson — telling a high-signal interview from a low-signal one is itself a skill.

## Key Ideas

- **Proximity search is a geospatial-index problem, not a DB-flavour problem.** You can't scan 100M drivers per request. Bucket the world into cells and index `cell → drivers`, so a query touches only the rider's cell + its neighbours. The real design axis is the **cell scheme**: fixed grid (simple, but hotspots), **geohash** (Z-order string prefix = coarser cell — what Redis `GEO`/`GEOSEARCH` uses under the hood), **QuadTree** (adaptive: subdivides dense areas), **S2** (Google, spherical cells on a Hilbert curve), **H3** (Uber's actual choice: hexagons → uniform neighbour distance, no corner ambiguity). Reaching for a **graph DB** here (as I did) is a tool-fit error: proximity is a 2-D range/radius query, not relationship traversal.
- **The location-update write path is the dominant load, not the match path.** 100M drivers pinging location every ~4s ≈ **~25M writes/s** of `driver → cell` churn — orders of magnitude above the ~230 rides/s match load. I waved this off as "not that critical"; it's actually the heaviest thing in the system. It wants a write-optimized, in-memory, sharded hot store (Redis per zone, or an in-memory geo-index) and only *cell-crossing* updates need to touch the index, not every ping.
- **The hot cell is the true crux — and we both missed it.** A stadium letting out, an airport, a surge zone → one cell holds a huge density of riders *and* drivers. A fixed grid turns that into a hotspot: one shard, one partition, melts. This is the **spatial twin of hot-key write contention** ([[wiki/system-design-concepts/hot-key-write-contention]]): aggregate QPS shards trivially, concentration on one cell does not. Mitigations: adaptive subdivision (QuadTree splits the dense cell), per-zone sharding with elastic consumers, and treating the surge cell specially. This is exactly the depth the interview should have driven and didn't.
- **Dispatch is a two-sided offer protocol, and its correctness lives in the atomic claim.** Matching a driver isn't assignment — the driver must *accept*. Rank candidates (proximity + ETA + rating + accept-likelihood), then either **offer sequentially** (one at a time, short timeout — simple, no double-accept) or **broadcast to top-N** (parallel, first-accept-wins — faster, but now you must prevent double-assignment). First-accept-wins is only correct if acceptance is an **atomic conditional claim** on *two* invariants — one rider gets one driver (`ride: OFFERED→ASSIGNED` via CAS) **and** one driver gets one ride (`driver: FREE→ON_TRIP` via CAS) — done as a single atomic op (Redis Lua / DB txn with unique constraint / fencing token). Losing offers are then cancelled. An **in-process lock** (what I reached for) is *not* a fence: requests can land on different instances, and the instance can crash mid-claim.
- **A probabilistic structure is a pre-filter, never the source of truth for a correctness decision.** I tried to use a **Bloom filter** as the authoritative "is this driver available?" check and claimed it was "100% accurate" — wrong. Membership is *"definitely absent"* (certain) xor *"possibly present"* (may be a false positive). It can cheaply **pre-filter** obviously-unavailable drivers to cut load, but the no-double-assignment guarantee must be closed by the atomic claim above. If you do use one, insert the set so that the **certain** direction is the safe one and the **uncertain** direction merely costs a skipped opportunity, not a correctness violation. See [[wiki/theory/bloom-filters]] (anti-pattern section, added from this round).
- **Absorb spikes with a durable queue; size concurrency with Little's Law.** Fronting the match service with a partitioned ride-request queue (so a crash doesn't drop in-flight requests, and consumers scale on queue depth) was the right instinct. And `concurrency = arrival × service-time = 20,000/s × 30s = 600,000` outstanding requests is textbook Little's Law — the one estimation move that landed cleanly.

## My Understanding

- I got the **shape** right — grid/cells for geo, per-zone Redis sharding, a spike-absorbing partitioned queue, stateless elastic match workers, read replicas, requeue-on-no-match — and Little's Law for outstanding-request sizing was clean. That's a competent HLD and the interviewer credited it.
- **Estimation wobbled again (G1).** I computed rides/s by dividing 20M by ~10^4 instead of ~86,400 (≈10^5) and got **2,000/s instead of ~230/s** — a 10× error, and I even talked myself *out* of the correct 200 I'd first said. It took the interviewer (post-reconnect) to reset it. Same seconds-in-a-day slip pattern as ID-gen/ChatGPT. The primitive I keep missing: **86.4k s/day ≈ 10^5**, so a "per day → per second" divide is ÷~10^5, not ÷10^4.
- **The Bloom-filter detour was a real conceptual miss (G2/G3).** I introduced a probabilistic structure into the *correctness-critical* path and asserted certainty it doesn't have. Good that I flagged it myself in the self-assessment; bad that correctness ("once matched, stays matched" — my own stated top NFR) is exactly where I should have run the check before the interviewer did. The authoritative mechanism is a boring atomic CAS on the ride/driver records; the Bloom filter is at best an optimization I didn't need.
- **I never entered the true crux — the hot cell (G3).** I named that a big city "could have multiple zones" and moved on. The genuinely hard, Uber-defining problem — surge concentration in one cell, and adaptive vs fixed cells — I never opened. This is the comfort-zone-drift pattern: I stayed with the familiar (queues, replicas, sharding-by-zone) instead of the unfamiliar-hard (geo-hotspot handling). Note this is the **same class of crux** as the auction's hot key — I've now seen it twice and should recognize the shape on sight.
- **I hand-waved the assignment seam (G2).** "The service has a lock" is not a distributed fence. The instant I broadcast to N drivers in parallel, I owed an atomic, cross-instance claim mechanism and a loser-cancellation path, and I should have named it unprompted.
- **The location-update path deserved more respect.** Calling ~25M writes/s "not that critical" is the kind of under-weighting that, in a stronger interview, would have been the follow-up that sank the round.

## On the interview quality

This is the meta-lesson from this artifact — recorded because *reading the room's signal quality* is itself an interview skill.

- **The feedback was generic and largely non-technical.** The headline note — "show more scope-*up* / build-the-next-big-thing / plan for Uber Eats" — is, taken at face value, **shaky system-design advice**: in a 40-min screen the expected move is to scope *down* and go deep, and gold-plating usually reads as poor prioritization. There *is* a real kernel — at Staff+, showing you see dispatch as a **reusable platform primitive** (rides, Eats, couriers all share it) is a legitimate signal — but it was delivered as "build 4–5 use cases," which conflates product ambition with engineering judgment. Keep the kernel, discard the framing.
- **The interviewer missed the actual technical content.** The one concrete gap he named was that I didn't say the *name* of the grid technique (and he couldn't recall it either — "quadtree"/"geohash"). Meanwhile the two genuinely weak spots — the Bloom-filter misuse (which *I* raised and he let pass) and the hand-waved distributed lock — went unprobed, and the real crux (hot cell) was never teed up. A strong systems interviewer pounces on a probabilistic-structure-in-the-correctness-path the moment it appears.
- **Small tells:** affirming my muddled Bloom claim with "with 100% accuracy" (the certain direction of a BF is only the *negative*, and only sometimes what you want); closing with an explicit "give me four stars." None disqualifying alone; together, low-signal.
- **Calibration takeaway:** an interviewer's title is unverifiable and the *content* of their feedback is the only thing you can grade. When feedback is generic ("show initiative," "think bigger") and never touches your actual design decisions, **self-source the technical critique** (as this extract does) rather than updating hard on the verdict. A weak interview is not evidence you did well *or* badly — it's just low-information; go find the signal yourself.

## Interview-Craft Lessons

- **"Per day → per second" is ÷10^5, not ÷10^4.** 86,400 s/day ≈ 10^5. Bake this so the 10× slip stops recurring. After every estimate, still say the sentence: *"what does this number — and its distribution — force me to do?"*
- **Name the crux, then go in.** When the problem is "find nearby X at scale," the hard part is almost never the happy-path lookup — it's the **hotspot** (dense cell / hot key) and the **churn** (update write volume). Force three minutes inside the hotspot before widening.
- **When you decouple or broadcast, close the seam.** Parallel offers ⇒ owe an atomic claim (two invariants: one-driver-per-ride *and* one-ride-per-driver) + loser cancellation + fencing. Never let "the service holds a lock" stand as the distributed answer.
- **Probabilistic structures pre-filter; atomic ops decide.** Before putting a Bloom filter / sketch on a correctness path, ask: which direction is *certain*, and is the *uncertain* direction merely a lost opportunity rather than a wrong answer? If correctness rides on it, you need an authoritative op instead.
- **Right tool for proximity:** geohash / QuadTree / S2 / H3 / Redis `GEO`, not a graph DB. Say the name; know why hexagons (H3) beat squares (uniform adjacency).
- **Grade the interviewer by their questions, not their résumé.** If the feedback never touches your design, self-source the critique.

## Open Questions

- **Adaptive vs fixed cells at the hotspot:** concretely, how does a QuadTree (or H3 resolution change) re-shard a surge cell *online* without dropping in-flight location updates or match queries? What triggers the split/merge?
- **Location-update write path:** at ~25M pings/s, what actually absorbs it — client-side dead-reckoning + only-emit-on-cell-cross? A write-behind buffer? What's the freshness SLA on `driver → cell` before matches start missing?
- **Atomic claim mechanism, exactly:** Redis Lua CAS on `{ride_id, driver_id}` vs a DB txn with a unique constraint vs a fencing token — which, and how does the loser-cancellation propagate to the other N-1 drivers' apps over the WebSocket?
- **Match fairness / starvation:** broadcasting to top-5 by proximity can starve lower-rated or farther drivers indefinitely; where does fairness/earnings-balancing live without wrecking rider ETA?
- **Cross-zone edge:** a rider at a zone boundary — how do neighbouring-shard candidate sets get unioned without double-offering the same driver from two shards?

## Connections

- Spatial twin of: [[wiki/system-design-concepts/hot-key-write-contention]] — the hot *cell* is the hot *key* in 2-D; aggregate load shards, concentration doesn't. The crux I failed to enter here is the same shape I *did* enter in the auction round.
- Closes correctness with: [[wiki/system-design-concepts/exactly-once-semantics]] — one-driver-one-ride is an idempotent atomic claim (conditional write keyed by `ride_id`/`driver_id`), the "dedup-by-key + atomic commit" pattern.
- New pattern pages from this round: [[wiki/system-design-concepts/geospatial-indexing]] (proximity search + hot cell) and [[wiki/system-design-concepts/dispatch-and-matching]] (offer protocol + atomic claim).
- Anti-pattern extracted to: [[wiki/theory/bloom-filters]] — probabilistic structure on a correctness path; which direction is certain.
- Spike absorption / elastic consumers reuse: [[wiki/system-design-concepts/the-log-abstraction]] and [[wiki/tech/kafka]] (partitioned ride-request queue, scale on depth); return-path to the rider echoes [[wiki/system-design-concepts/async-response-routing]] (match result comes back asynchronously over the rider's channel).
- Estimation primitives: [[wiki/theory/latency-numbers]]; Little's Law (`concurrency = QPS × latency`) as applied for the 600k outstanding-requests figure.
- Partitioning lens: [[wiki/system-design-concepts/hash-vs-range-partitioning]] — sharding the geo index by zone, and why a hot cell defeats it.

## Concepts flagged for wiki (pending /update-wiki)
- **Geospatial indexing / proximity search** — cell schemes (grid/geohash/QuadTree/S2/H3), read (ring query) vs write (location churn) paths, the hot-cell hotspot, why not a graph DB. *(new)*
- **Dispatch & matching (two-sided offer)** — rank → offer sequential vs broadcast top-N → first-accept-wins → atomic two-invariant claim → timeout/requeue; assignment correctness lives in the claim, not a lock. *(new)*
- **Bloom filter as pre-filter, not authority** — certain-vs-uncertain direction; correctness-critical membership needs an atomic op. *(extend bloom-filters)*
- **Hot cell = hot key in 2-D** — cross-link the spatial hotspot to write-contention. *(extend hot-key-write-contention)*
