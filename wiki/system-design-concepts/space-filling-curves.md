# Space-Filling Curves & Hierarchical Cell Systems

The trick that lets an ordinary **B-tree index 2-D space**. A space-filling curve maps grid cells to a single sortable
number such that **near-in-space ⇒ near-in-key**. That one property is what makes geohash, S2, and lakehouse spatial
clustering work — and understanding it explains the deep design split between **S2 and H3**.

## Why collapse 2-D to 1-D at all

A 2-D proximity query has no natural total order, so you can't index it with the most battle-tested structure on Earth (a
B-tree) directly. A space-filling curve fixes this: assign each cell its position along the curve as a **1-D key**, then:

1. **Any B-tree / KV store / sorted file becomes a spatial index** — geohash strings, S2 cell IDs, and H3 indexes are all
   "coordinate → 1-D key" encodings.
2. **A bounding-box query becomes a few contiguous key ranges** (`WHERE key BETWEEN lo AND hi`) — *only* because the curve
   preserves locality. Without it, a box would scatter across the whole key space.
3. **Lakehouse data-skipping works** — sort/cluster Parquet by the curve value and each file's **min/max covers a compact
   spatial patch**, so Iceberg/Delta prune 90%+ of files by metadata alone. See [[system-design-concepts/hash-vs-range-partitioning]].

## Z-order (Morton) vs Hilbert

| Curve | How | Locality | Cost |
|---|---|---|---|
| **Z-order (Morton)** | **bit-interleave** x and y (`x=a₂a₁a₀, y=b₂b₁b₀ → b₂a₂b₁a₁b₀a₀`) | good, but **long jumps** at quadrant ends → a box fragments into many disjoint ranges | dirt cheap (pure bit ops) |
| **Hilbert** | rotate/reflect the sub-curve per quadrant so ends line up | **no long jumps** — every step is edge-adjacent → fewer, longer contiguous ranges | pricier to compute |

**A geohash is literally a Z-order curve** — interleaved lng/lat bits, then base-32 encoded. Z-order's cheapness is why
it's everywhere (geohash, Iceberg `zorder()`); Hilbert's superior locality is why **S2 uses it** and why modern clustering
is migrating Z-order → Hilbert. Fewer contiguous ranges = fewer index seeks = faster range and kNN queries.

## The payoff for grid systems: exact vs approximate hierarchy

Both **S2** and **H3** do the same two-step move — **project the sphere onto a polyhedron, then tile each flat face and
refine into finer resolutions**. The deep difference is *how the resolutions nest*.

### S2 — project to cube faces, then quadtree (exact)

Two distinct steps, and the reason for each:

- **Project (sphere → 6 flat squares).** You can't lay a regular recursive grid on a curved sphere, so S2 wraps the globe
  in a **cube** and pushes each point out to one of the 6 faces → `(face 0–5, u, v)`, plain flat coordinates. A nonlinear
  transform then squeezes corner-vs-center area distortion from ~5× to ~2×. One curved problem → six flat-square problems.
- **Quadtree each square.** Recursively split into 4 quadrants, 30 levels, down to ~1 cm².

**Why a quadtree and not just a flat grid of cells on the face?** A quadtree *is* dividing into cells — it just keeps
**every resolution at once, nested** (a flat single-resolution grid is one horizontal slice of it). The nesting buys three
things a flat grid throws away:

1. **Containment = prefix.** Exact 4-way splits make a cell's ID a literal prefix of all descendants'. `S2CellId` =
   `3 bits face + 2 bits/level (quadrant path) + sentinel`; parent/child is a bit-shift.
2. **Region = contiguous integer ranges.** Number leaves along a **Hilbert curve** → any parent cell covers a contiguous
   run of leaf IDs → region query = a few B-tree range scans.
3. **Adaptive covering.** Cover a polygon with big cells inside and small cells on the boundary — only possible because
   levels nest.

### H3 — project to icosahedron, tile with hexagons (approximate)

Clarification first: after projection **H3's face is a *triangle*** (icosahedron, 20 triangular faces); the **hexagons are
the cells tiled on top**, not the faces. Exactly **12 cells must be pentagons** (at the icosahedron vertices) — Euler's
formula forbids tiling a sphere with hexagons alone.

H3 *does* have a parent/child hierarchy and a digit-path ID — but it refines **aperture 7** (~7× cells per level), and
**7 hexagons cannot exactly tile 1 hexagon**. To fit ~7 children, the child grid is **rotated ≈19.1°**, so **children
straddle parent boundaries**. The hierarchy is therefore **approximate**: a digit-prefix is *not* an exact region, so H3
gets no clean range-scan trick and roll-ups between resolutions are inexact.

### The tradeoff (they optimized for opposite things)

| | S2 (squares, aperture-4 quadtree) | H3 (hexagons, aperture-7) |
|---|---|---|
| Projection | cube → square faces | icosahedron → triangle faces |
| Cell shape | squares (4 edge + 4 corner neighbors, unequal) | hexagons (6 equidistant, edge-sharing) + 12 pentagons |
| Refinement | ÷4, children **exactly partition** parent | ×7, children **rotated ~19°, straddle** parent |
| Hierarchy | **exact** nesting → prefix containment | **approximate** nesting |
| Superpower | contiguous **range-scan region queries**, clean roll-ups | **uniform adjacency** for flow / demand-supply / ML |
| Weak spot | uneven neighbor geometry | no exact range indexing; pentagon anomalies |

S2 chose squares *because* exact 4-way nesting yields prefix IDs and range-scan region queries — an **indexing** tool.
H3 chose hexagons *because* uniform neighbors matter for movement and spatial aggregation — an **analytics** tool — and
accepted an inexact hierarchy as the price.

## Interview angle

> "Space-filling curves are the trick that lets a B-tree index 2-D space: map each cell to a 1-D key where near-in-space
> means near-in-key. Z-order is just bit-interleaving — cheap, and literally what a geohash is — but it has long jumps, so
> a query box fragments into many key ranges. Hilbert rotates the sub-curve per quadrant to kill the jumps, giving fewer
> contiguous ranges; that's why S2 numbers its cells along a Hilbert curve. The locality is also what makes lakehouse
> min/max pruning work when you cluster Parquet by the curve. And it explains S2 vs H3: **S2's aperture-4 quadtree nests
> exactly**, so a cell ID is a prefix and a region is a contiguous ID range (indexing); **H3's aperture-7 hex hierarchy
> only nests approximately** — rotated children straddle parents — so you trade range indexing for uniform-neighbor
> geometry (analytics)."

## Connections
- [[system-design-concepts/geospatial-indexing]] — the cell schemes (geohash/quadtree/S2/H3) whose 1-D keys these curves produce; the read/write paths and hot-cell crux
- [[system-design-concepts/hash-vs-range-partitioning]] — a curve value is the **range key** that makes geohash prefixes and Iceberg min/max pruning work; contrast with hash sharding
- [[system-design-concepts/commutative-aggregation]] — distributed kNN merges per-file **local top-k** (a mergeable monoid) after curve-clustered files are pruned by zone maps
- [[system-design-concepts/cache-stampede]] — cache spatial queries by quantizing q to a fixed-level cell key (geohash prefix, or a truncated quadtree quadrant-path = Morton code)
- [[theory/bloom-filters]] — another "encode structure into a compact key/summary to skip work" idea

## Sources
- [[sources/docs/knn-geospatial-search-explainer]] — interactive kNN explainer: brute force → ANN → trees → grids → Z-order/Hilbert curves → S2/H3 exact-vs-approximate nesting → caching
