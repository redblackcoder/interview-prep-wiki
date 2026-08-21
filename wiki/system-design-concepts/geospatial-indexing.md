# Geospatial Indexing (Proximity Search)

The problem behind "find nearby X" (drivers, restaurants, scooters, friends): given a point, return the members within radius r **without scanning the whole set**. A linear scan of 100M drivers per request is impossible, so you need a spatial index that turns "near me" into a bounded key lookup.

## The core idea: bucket space into cells

Assign every entity a **cell** derived from its lat/long, and index `cell → members`. A query maps the query point to its cell and gathers that cell **plus its neighbours** (a ring wide enough to cover r). Now each query touches a handful of cells, not the globe.

The design axis is the **cell scheme**:

| Scheme | Shape | Property | Used by |
|---|---|---|---|
| **Fixed grid** | squares, uniform | trivial; but dense areas become hotspots | naive |
| **Geohash** | lat/long interleaved → base32 string | prefix = coarser cell; range-scannable | Redis `GEO` (52-bit geohash in a ZSET) |
| **QuadTree** | squares, **adaptive** | subdivides only dense regions → tames hotspots | in-memory indexes |
| **S2** | spherical cells on a Hilbert curve | no polar distortion; hierarchical cell IDs | Google |
| **H3** | **hexagons** | uniform neighbour distance, no corner ambiguity | **Uber** |

Hexagons (H3) win for dispatch because all six neighbours are equidistant — square grids have edge-neighbours closer than corner-neighbours, which distorts "expand the search ring."

**Not a graph DB.** Proximity is a 2-D range/radius query, not relationship traversal. Reaching for Neo4j here is a tool-fit error; the right tools are Redis `GEOSEARCH`, PostGIS, an ES geo index, or an in-memory QuadTree/S2/H3.

## Two paths, and the write path is the heavy one

- **Read (match) path:** point → cell → union of ring cells → filter by radius/attributes → rank. Cheap per query.
- **Write (location) path:** every moving entity emits location continuously. 100M drivers pinging every ~4s ≈ **~25M writes/s** of `entity → cell` churn — usually **orders of magnitude above** the match QPS. Under-weighting this is a classic miss. Mitigate with: emit-on-cell-crossing (not every ping), client-side dead-reckoning, a write-behind buffer, and an in-memory/Redis hot store sharded by zone. Only cell-*crossings* need to mutate the index.

## The hot cell (the real crux)

A stadium emptying, an airport, a surge zone → one cell holds enormous density of both demand and supply. A fixed grid turns that into a **hotspot**: one shard/partition takes all the load and melts. This is the **spatial twin of [[system-design-concepts/hot-key-write-contention]]** — aggregate QPS shards trivially, but *concentration on one cell* does not, because everyone there must query/update the same bucket.

Mitigations:
- **Adaptive cells** (QuadTree split / raise H3 resolution) so the dense area becomes many small cells.
- **Per-zone sharding + elastic consumers** so a hot zone scales independently.
- Treat the surge cell specially (dedicated capacity, coarser matching).

Cell-size is itself a tradeoff: too large → too many candidates to scan per query; too small → must union many neighbour cells to cover r, and more cell-crossing writes.

## Interview angle

> "Proximity search is a spatial-index problem: bucket space into cells (H3 hexagons like Uber, or Redis geohash), index `cell → members`, and a query touches only the local ring. The two things people miss: the **location-update write path** is the dominant load, not the match lookup; and the **hot cell** — a stadium or airport — is the real scaling crux, the 2-D version of a hot key. Fixed grids hotspot there; adaptive cells (QuadTree/H3 resolution) and per-zone sharding are the fix. It is *not* a graph-DB problem."

## Connections
- [[system-design-concepts/hot-key-write-contention]] — the hot cell is a hot key in 2-D; the same shard-can't-help-concentration argument
- [[system-design-concepts/dispatch-and-matching]] — proximity search produces the candidate set that the offer protocol then ranks and offers
- [[system-design-concepts/hash-vs-range-partitioning]] — sharding the geo index by zone; geohash prefixes as a range key
- [[system-design-concepts/rds-vs-key-value-store]] — why the live location store is an in-memory KV (Redis), not the relational registration DB
- [[theory/latency-numbers]] — why the hot location store must be in-memory to sustain the write churn

## Sources
- [[sources/docs/design-uber-driver-allocation-mock-interview]] — grid/cells for driver location; the hot cell and location-write-path were the un-entered cruxes
