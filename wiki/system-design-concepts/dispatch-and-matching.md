# Dispatch & Matching (Two-Sided Offer)

Assigning a unit of demand to a unit of supply where **the supply side must consent** — ride-hailing driver dispatch, food-delivery courier assignment, ad-hoc task routing. Unlike pure assignment (scheduler → worker), the chosen party can **decline**, so it's an *offer protocol*, and its correctness lives in one place: the **atomic claim**.

## The pipeline

1. **Candidate set** — proximity search ([[system-design-concepts/geospatial-indexing]]) yields nearby available supply.
2. **Rank** — score by ETA, rating, acceptance-likelihood, fairness/earnings-balance, attribute filters (vehicle class, etc.).
3. **Offer** — push to candidates and wait for accept (see sequential vs broadcast below).
4. **Claim** — first accept wins, *atomically*.
5. **Timeout / requeue** — offers expire; on all-decline re-rank and re-offer, expand radius, or requeue; enforce an overall deadline + notify the requester.

## Offer strategy: sequential vs broadcast

- **Sequential** (offer one at a time, short per-offer timeout): dead simple, no double-accept possible, but latency stacks with each decline.
- **Broadcast top-N** (offer to N in parallel, first-accept-wins): fast, but now two hazards appear — **double-assignment** (two drivers accept) and **stale offers** (offering a driver who just got taken). Broadcast is only correct with an atomic claim + loser cancellation.

## The atomic claim (where correctness lives)

First-accept-wins must enforce **two invariants at once**:
- **one ride → one driver:** `ride: OFFERED → ASSIGNED` only if still OFFERED.
- **one driver → one ride:** `driver: FREE → ON_TRIP` only if still FREE (a driver may be in two broadcasts simultaneously).

Do both as a **single atomic conditional op** — Redis Lua CAS over `{ride_id, driver_id}`, a DB transaction with a unique constraint, or a fencing token — so concurrent accepts collapse to exactly one winner. This is the [[system-design-concepts/exactly-once-semantics]] "dedup-by-key + atomic commit" pattern. The losers get an explicit "already taken" over their channel.

**Anti-pattern:** an **in-process lock** in one service instance is *not* a distributed fence — requests can hit different instances, and the instance can crash mid-claim. The claim must live in the shared store, not in a worker's memory.

## Keeping the candidate pool honest

Offering drivers who are already busy wastes offers and slows matches, so "matched → removed from available" must propagate promptly. The **source of truth** for availability is the driver-status store, updated at claim time — **not** a probabilistic side structure. A [[theory/bloom-filters|Bloom filter]] can *pre-filter* obviously-unavailable candidates to cut load, but it can never be the authority for the no-double-assignment guarantee (see its anti-pattern note).

## Async by construction

The moment you put a queue/offer hop between request and assignment, matching is **asynchronous**: the synchronous response is an *ack* ("finding a driver…"), and "driver found / none available" returns later over the rider's channel — an [[system-design-concepts/async-response-routing]] back-channel. Say this explicitly; don't imply instant assignment.

## Interview angle

> "Dispatch is a two-sided offer, not an assignment — the driver can decline. I rank nearby candidates and broadcast to the top-N for latency, but first-accept-wins is only correct behind an **atomic claim** that enforces one-ride-one-driver *and* one-driver-one-ride in a single conditional write, with the losing offers cancelled. A lock in the service instance isn't a fence. Availability's source of truth is the driver-status store; a Bloom filter is at most a pre-filter."

## Connections
- [[system-design-concepts/geospatial-indexing]] — supplies and ranks the candidate set; a hot cell concentrates offers on the same few drivers
- [[system-design-concepts/exactly-once-semantics]] — the atomic claim is dedup-by-key + atomic commit; idempotent under retries
- [[system-design-concepts/async-response-routing]] — the match result returns asynchronously to the exact requester
- [[system-design-concepts/hot-key-write-contention]] — a surge cell makes many riders contend for the same drivers → contention on the claim
- [[theory/bloom-filters]] — why it's a pre-filter here, never the assignment authority

## Sources
- [[sources/docs/design-uber-driver-allocation-mock-interview]] — offer-to-top-5 with WebSocket accept; the atomic claim was hand-waved to an in-process lock
