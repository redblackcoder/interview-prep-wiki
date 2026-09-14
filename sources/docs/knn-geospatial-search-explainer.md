---
source: knn-search-geospatial.html (interactive explainer, built in-session)
source_url: local — interview-prep/knn-search-geospatial.html
type: doc
date_extracted: 2026-09-07
topic: system-design-concepts
---

# kNN Search — from Brute Force to Space-Filling Curves

Distilled from a self-authored **interactive HTML explainer** (14 animated canvas demos) covering how
k-nearest-neighbor search is implemented at every scale, with a deep focus on 2-D geospatial indexing.
The through-line question: *how do you avoid looking at most of the points?* Either a **tree that prunes
branches**, or a **grid/curve that maps space to a sortable 1-D key** so a plain B-tree becomes a spatial index.

## Key Ideas

- **kNN's core tension.** Given points `P` and query `q`, return the k closest. Naïve scan is `O(N·d)` per query.
  Everything else is a way to skip most points. Two problem shapes: **high-dim vector search** (embeddings; curse of
  dimensionality kills tree pruning → ANN like **HNSW / IVF-PQ**), and **low-dim geospatial** (2-D lat/lng; pruning works,
  exact answers are cheap).

- **Single node.** `<1M` → flat brute force (GPU exact, gold standard). `1M–100M` → HNSW (best recall/latency) or IVF-PQ
  (smallest memory) in RAM. `> RAM` → DiskANN. 2-D geo → tree/grid index.

- **Distributed kNN on a lakehouse (S3 / Iceberg).** No always-on index server; ephemeral compute (Spark/Trino/DuckDB).
  Two mechanisms: (1) **partition + prune** — Iceberg manifests store per-file **min/max** (zone maps); the planner skips
  files whose key range can't contain answers. (2) **scatter–gather** — surviving files scanned in parallel for a **local
  top-k**, then merged (top-k is a mergeable monoid, so it parallelizes perfectly). The critical layout decision: physically
  **sort/cluster rows by a spatial key** (space-filling-curve value) so nearby points share files and min/max pruning works.
  Iceberg `WRITE ORDERED BY zorder(lat,lng)`; Databricks liquid clustering.

- **Two families for 2-D geospatial kNN:**
  - **Tree-based (recursive space partitioning):** carve the plane, descend only into regions that could hold a neighbor.
    - **kD-tree** — alternating x/y median splits; NN search backtracks, only crossing a split line if it's closer than the
      current best (the pruning). `O(N log N)` build, `O(log N)` avg query. Bad updates, dies in high dim.
    - **Quadtree** — recursive 4-way split on **space** (fixed geometry), cell splits at capacity; depth ∝ density.
    - **R-tree** — groups objects into nested **Minimum Bounding Rectangles**; balanced, B-tree-like; handles polygons;
      MBRs may overlap. The DB spatial index (PostGIS, SQLite R*Tree).
  - **Grid / geohash-based (discretize + encode):** chop the surface into cells with a string/int key; kNN = "my cell +
    neighbor ring" via an ordinary B-tree/hash index.
    - **Geohash** — recursively bisect world, **interleave lng/lat bits** (this IS a Z-order curve), group 5 bits → base-32
      char. Shared prefix ⇒ proximity. Query = prefix + **8 neighbor cells** (edge effects), rank by exact distance.
      Gotchas: cell-boundary edge effects (neighbors mandatory), pole distortion (lat/lng grid, not equal-area).
    - **S2** — project sphere onto **6 cube faces**, quadtree each face (30 levels, ~1cm²), number cells along a **Hilbert
      curve** → 64-bit `S2CellId`. A contiguous range of IDs = a compact sphere patch → region query = a few integer range scans.
    - **H3** — tile the world with **hexagons** (12 pentagons unavoidable) on an **icosahedron**. All 6 neighbors equidistant
      (squares have unequal edge vs corner neighbors). kNN = q's cell + `kRing(k)`, grow until enough.

- **Space-filling curves are the unifying trick.** A curve maps 2-D grid cells to a single sortable number that preserves
  locality (near in space ⇒ near in key).
  - **Z-order (Morton):** **bit-interleave** x and y. Cheap (pure bit ops) — literally what a geohash is. Weakness: **big
    jumps** at quadrant ends → a query box fragments into many disjoint key ranges.
  - **Hilbert:** rotates/reflects the sub-curve per quadrant so **every step is to an edge-adjacent cell — no long jumps**.
    Best locality → fewer, longer contiguous ranges for a box. This is why **S2 uses it** and why lakehouse clustering is
    moving from Z-order to Hilbert.
  - **Why they supercharge grid/geohash approaches:** (1) collapse 2-D → 1-D so a **B-tree** indexes space; (2) locality
    makes a bounding box become a few `WHERE key BETWEEN lo AND hi` ranges; (3) sorting Parquet by a curve makes each file's
    **min/max cover a compact patch**, enabling lakehouse data-skipping.

- **S2 vs H3 — exact vs approximate hierarchy (the deep distinction).** Both = project to a polyhedron, then tile+refine.
  - "Project then quadtree the face" (S2) = two steps: **project** sphere → 6 flat square faces `(face,u,v)` (can't grid a
    curved sphere; a nonlinear transform cuts corner-vs-center area distortion from ~5× to ~2×), then **quadtree** each square.
  - **Why quadtree, not a flat single-resolution grid?** A quadtree *is* dividing into cells — it keeps **every resolution
    nested at once** (a flat grid is one level of it). Nesting buys three things a flat grid loses: **containment = ID prefix**
    (`3 bits face + 2 bits/level + sentinel`), **region = contiguous integer ranges** (via Hilbert numbering → B-tree range
    scans), and **adaptive covering** (big cells inside a polygon, small on the boundary).
  - **Does H3 build a tree too?** It has a parent/child hierarchy and a digit-path ID, but refines **aperture 7** (×7 cells/
    level), and **7 hexagons can't exactly tile 1 hexagon** — children are **rotated ≈19.1°** and **straddle parent boundaries**.
    So the hierarchy is **approximate**: a digit-prefix isn't an exact region → no clean range-scan trick, inexact roll-ups.
    (Clarification: H3's *face* after projection is a **triangle** (icosahedron); hexagons are the cells tiled on top.)
  - **The tradeoff:** S2 chose squares *because* exact aperture-4 nesting yields prefix IDs + range-scan region queries (an
    **indexing** tool). H3 chose hexagons *because* uniform neighbors matter for movement/demand-supply/ML aggregation (an
    **analytics** tool) — accepting an inexact hierarchy as the price.

- **Caching kNN queries.** The query point is a **continuous coordinate** → a raw-coordinate cache key has ~0% hit rate.
  Trick: **quantize the query to a cell** so nearby queries collapse to one key, and **cache the candidate set (not the final
  answer)** so you re-rank exactly per request in memory. Granularity is the dial (coarser = more hits, bigger candidate set).
  - **Geohash:** the key *is* the truncated hash (`gcpuv`) + its 8-neighbor ring; evict cell + neighbors on change.
  - **Quadtree (the tricky case):** variable leaves + reshaping tree make "key on the leaf" fragile. Insight: a cell's
    address = its **root-to-cell quadrant path** (NW/NE/SW/SE = 0/1/2/3) = a **base-4 Morton/Z-order code**. Two strategies:
    (1) **truncate the quadrant path to a fixed level** → a stable geohash-like key decoupled from the mutable deep tree
    (recommended for caching); or (2) **per-node versioned cache** — a mutation bumps the version of every ancestor to the root
    (`O(depth)`), invalidating exactly the covering entries. Unifying takeaway: **a stable, query-point-derivable, fixed-
    granularity 1-D key is what makes both indexing and caching tractable** — another face of why space-filling curves matter.
  - Cross-cutting: moving objects → short TTL / event-driven eviction; negative caching for empty cells; key must include
    granularity, `k`, and any filters.

## Interview angle

> "kNN is 'avoid looking at most points.' In high dimensions that's an ANN graph (HNSW). In 2-D geo it's either a **tree**
> that prunes branches (kD-tree/quadtree/R-tree) or a **grid keyed by a space-filling curve** so a B-tree does the work
> (geohash = Z-order bits; S2 = cube quadtree numbered by a **Hilbert curve**; H3 = hexagons for uniform neighbors). The
> curve is the whole trick: near-in-space ⇒ near-in-key, so a bounding box becomes a few contiguous range scans, and in a
> lakehouse it makes Parquet min/max pruning work. **S2's aperture-4 quadtree nests exactly** (prefix IDs, range-scan
> regions); **H3's aperture-7 hex hierarchy only nests approximately** (rotated children straddle parents) — indexing tool
> vs analytics tool. For caching, quantize the query to a fixed-level cell (a geohash prefix, or a truncated quadtree
> quadrant-path = Morton code) and cache the candidate set, not the answer."
