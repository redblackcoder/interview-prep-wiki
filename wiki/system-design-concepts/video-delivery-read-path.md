# Video Delivery Read Path (Prefetch + Adaptive Bitrate + Segments)

The read path for a scroll-video product (YouTube Shorts, Reels, TikTok) is a distinct design from the feed. The feed decides *which* videos and returns tiny IDs + URLs; this path is about turning a swipe into pixels in `<100ms` and serving the bytes cheaply at a 1000:1 read:write ratio. Three mechanisms carry it: **client prefetch**, **adaptive bitrate (ABR)**, and **segmentation** — and segmentation is simultaneously the startup-latency lever and the CDN cache-hit lever.

## The one-sentence mental model

> **`<100ms` playback is not a server latency — it's "the client already has the next video's first segment before the swipe." Segment every video into short immutable chunks so the player can start after one chunk, switch bitrate at chunk boundaries, and let the CDN cache each chunk as its own key. The feed budget (fetch IDs) and the bytes budget (stream video) are separate problems.**

## Two separate latency budgets

- **Feed-of-IDs budget** — return the next page of `post_id`s + metadata + CDN URLs. Small JSON; served from the feed cache. This is a "page the cursor" problem → [[system-design-concepts/feed-cursor-stability]].
- **Bytes budget** — actually stream the video. This is a CDN hit-ratio + client-prefetch problem. Conflating the two ("the feed is slow") hides which budget you're missing. The perceived `<100ms` is almost entirely the *bytes* budget, and it's won on the client.

## Mechanism 1 — Client prefetch

The swipe must hit an already-primed player. The client, while the user watches video *i*, **prefetches the manifest + first segment(s) of videos i+1..i+N** (N tuned to network + scroll speed). So "start playback" is a local buffer read, not a network round-trip.
- The feed API returns a small look-ahead window of upcoming posts precisely so the client *can* prefetch.
- Budget prefetch against data cost (don't prefetch 4K on cellular; prefetch fewer, lower-rendition first segments).
- This is the mechanism a design must *name and build* — asserting `<100ms` without prefetch is the classic gap.

## Mechanism 2 — Adaptive bitrate (ABR)

Transcode each video into a **rendition ladder** (e.g. 240p→1080p+). The player picks a rendition to fit screen + measured throughput, and **switches at segment boundaries** as conditions change — no rebuffer on a dip, no 4K wasted on a phone. This is also a serving-cost lever: most mobile viewers pull 480/720p, not the top rendition. Manifest formats: **HLS** (`.m3u8`) or **DASH** (`.mpd`).

## Mechanism 3 — Segmentation (and why it's the CDN lever)

Each rendition is chopped into short **segments** (~2–6s, fMP4/CMAF or TS). Wins:
1. **Startup** — play after one segment, not the whole file.
2. **ABR switch points** — the segment boundary is where the player changes rendition.
3. **Cacheability** — each segment is an **immutable object with its own URL → its own CDN cache key**; a 4s chunk requested by millions who reach that point is *one* cache key (~100% edge hit). See [[tech/cdn]].
4. **Seek + retry isolation** — seek = fetch the covering segment; a failed fetch retries just that chunk.

### Segments as storage objects — the object-count trade-off
In the standard HLS/DASH → CDN → object-store setup, **every segment is a separate S3 object** with a stable immutable URL (segment = object = cache key). A 30s Short at 4s segments × 5 renditions ≈ **~45 objects/video**; at 1M uploads/day ≈ **~45M new objects/day**.
- **Alternative:** one **CMAF file per rendition** addressed by **byte-range** in the manifest (CDN caches ranges) — far fewer objects, at the cost of range-cache complexity. The real fork: *many small objects* (simple keys, huge count) vs *few files + byte-ranges* (fewer objects, range caching). Low-Latency HLS/CMAF favors the latter.
- **Don't let object count force bucket `LIST`.** S3 is a flat keyspace; `LIST` is paginated (1000 keys/call), so listing tens of millions is slow. Design lifecycle so you never list:
  - **Lifecycle/TTL policies** expire staging/temp objects by prefix+age automatically.
  - **Metadata DB is the source of truth** — delete a video by **prefix-deleting `videos/{id}/…` with keys you already know** (batch `DeleteObjects`), never by listing.
  - **Orphan sweeps use S3 Inventory** (scheduled full-bucket manifest → offline reconcile vs DB), not live `LIST`. This is the "maintainer batch that can run long" done right.
  - **Key layout** `videos/{id}/{rendition}/{seg}` (optionally hash-prefixed) makes lifecycle rules, prefix-deletes, and Inventory partition cleanly.

## Key points
- `<100ms` start = **client prefetch of next-N first segments**, not a server SLA; the feed API returns a look-ahead window to enable it.
- **ABR** fits rendition to device+network and switches at segment boundaries; also cuts serving cost (most viewers aren't on the top rendition).
- **Segmentation** is a triple win: startup, ABR switch points, and per-segment CDN cacheability (one immutable key per chunk).
- Segment-per-object explodes S3 object count (~45/video); mitigate with byte-range CMAF *or* accept it and **drive cleanup from the metadata DB + lifecycle + S3 Inventory, never from `LIST`**.
- Keep the **feed-of-IDs budget** and the **bytes budget** separate when reasoning about "slow."

## Interview angle

> "Sub-100ms playback on a scroll feed isn't a server latency — it's that the client already has the next video buffered. So the feed API returns a look-ahead window of upcoming posts, and while you watch video i the client prefetches the manifest and first segment of i+1..i+N. Each video is transcoded into an adaptive-bitrate ladder and chopped into 2–6s segments: the player starts after one segment, switches rendition at segment boundaries as the network changes, and — crucially — each segment is an immutable object with its own URL, so the CDN caches it as one key shared by everyone who reaches that point. The catch is object count: ~45 objects per Short times a million uploads a day is ~45M objects daily. I either use byte-range CMAF to cut object count, or I accept it and make sure cleanup never lists the bucket — lifecycle policies expire staging objects, deletes are prefix-deletes off keys I already have in the metadata DB, and orphan sweeps run offline against an S3 Inventory report."

## Connections
- [[tech/cdn]] — segmentation is the hit-ratio lever; immutable segment URLs get effectively-infinite TTL; the CDN serves the bytes the feed only references
- [[system-design-concepts/timeline-fanout-hybrid]] — the feed returns the post IDs + URLs this path streams; the look-ahead window is what the client prefetches
- [[system-design-concepts/feed-cursor-stability]] — the feed-of-IDs budget: paging the upcoming window stably as follows change
- [[system-design-concepts/cache-stampede]] — a segment of a suddenly-viral video is the cold-object stampede case at the edge
- [[theory/latency-numbers]] — why a local buffer read beats even an edge fetch, and an edge fetch beats origin, for a <100ms budget
- [[system-design-concepts/cloud-database-cost-model]] — object-store cost/among the four axes; request + storage cost of ~45M objects/day
- [[theory/durability-rpo-rto]] — orphaned segments arise from half-failed writes; the DB-as-source-of-truth reconcile is the recovery

## Sources
- [[sources/docs/design-youtube-shorts-self-interview]] — the thin read path (asserted <100ms, no prefetch/ABR); segment crash course + S3 object-model/cleanup discussion
- [self-youtube-shorts-design/](https://github.com/redblackcoder/interview-prep-raw/blob/master/docs/self-youtube-shorts-design/) — transcript + whiteboard
