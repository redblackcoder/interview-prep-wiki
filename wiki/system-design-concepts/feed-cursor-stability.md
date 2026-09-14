# Feed Cursor Stability (Snapshot-Isolated Pagination)

An infinite-scroll feed is paginated, but the underlying data **mutates while you scroll**: new posts arrive, follows change, the ranking model re-scores, hot posts get stitched in ([[system-design-concepts/timeline-fanout-hybrid]]). Naïve pagination over a moving target makes items **duplicate, skip, or jump**. The fix is to page over a *pinned version* of the feed, not the live one — snapshot isolation applied to a feed.

## The one-sentence mental model

> **Give the scroll session a snapshot ID that pins the feed version at first request; page a stable cursor within that snapshot; let the generator copy-on-write a new version instead of mutating the pinned one; expire snapshots by TTL so you don't store versions forever.**

## Why offset pagination corrupts

`LIMIT 10 OFFSET 20` assumes the list is stable between pages. It isn't:
- **Insert above the cursor** (a new post, a stitched celebrity post) shifts everything down → the item at the old offset boundary is **served twice** (duplicate).
- **Delete/unfollow above the cursor** shifts everything up → an item is **skipped**.
- **Re-score** (the algo feed reorders) → items **jump** between pages, some seen twice, some never.

Offset is positional; positions move. You need an anchor that survives mutation.

## The two ingredients

**1. Cursor = a stable anchor, not an offset.** Page by "everything after `(score, post_id)`" (keyset pagination) rather than by numeric offset. The cursor names *where you were* in value-space, so inserts/deletes elsewhere don't shift it. This alone fixes append-only feeds.

**2. Snapshot for a mutating/ re-scored feed.** Keyset isn't enough when scores themselves change or hot posts are stitched at read time. So pin a **snapshot**:
- First feed request mints a **snapshot ID** bound to the feed version the user is reading.
- The cursor is interpreted **within that snapshot** — every page of this scroll session reads the same frozen version, so ordering and membership are stable.
- The **feed generator won't overwrite an in-use snapshot**: on a follow-change / new upload / new algo version it **copy-on-writes a new version** rather than mutating the pinned one (see [[theory/copy-on-write-vs-mvcc]]).
- Snapshots **expire by TTL** (e.g. minutes–hours) so the store keeps bounded versions, not one-per-session-forever. A returning user past TTL gets a fresh snapshot (and fresh content) — an acceptable UX trade.

## Where the snapshot lives

- The materialized feed is `user_id → [post_id]`; a snapshot is a versioned view of that (a version stamp on the row, or a copied version keyed `user_id:snapshot_id`).
- The cache tier respects the snapshot ID as part of the key, so paging is served from cache within a session.
- Hot-post stitching is done **once per (user, snapshot)** and cached, not re-run per page — the snapshot is also the natural cache unit.

## Key points
- Mutating feed + offset pagination = **duplicates, skips, jumps**; positions move under you.
- **Keyset cursor** (`after (score, id)`) fixes append/delete-elsewhere; it does not fix **re-scoring** or **read-time stitching**.
- **Snapshot isolation** pins a feed version per scroll session; the generator **COWs a new version** rather than overwriting an in-use one; **TTL** bounds version storage.
- The snapshot ID is part of the cache key, and the unit at which read-time stitching is amortized.
- Trade: bounded staleness within a session (you don't see brand-new items until the next snapshot) in exchange for a stable scroll.

## Interview angle

> "An infinite feed paginates over data that changes while you scroll — new posts, unfollows, the model re-scoring, celebrity posts stitched in at read time. Offset pagination breaks immediately: an insert above your position re-serves an item, a delete skips one, a re-score makes items jump. So first I page with a keyset cursor — 'everything after this (score, id)' — which is immune to inserts and deletes elsewhere. But that's not enough once scores themselves change, so I pin a snapshot: the first request mints a snapshot ID bound to the feed version, every page in that scroll session reads the same frozen version, and the feed generator copy-on-writes a new version instead of mutating one that's in use. Snapshots expire on a TTL so I'm not storing a version per session forever, and the snapshot ID is part of the cache key so paging stays cache-served and any read-time stitching is done once per snapshot."

## Connections
- [[theory/copy-on-write-vs-mvcc]] — the mechanism: a snapshot is an MVCC read view; the generator COWs a new version rather than mutating in place
- [[system-design-concepts/timeline-fanout-hybrid]] — why the feed mutates under you (follows change, hot posts stitched at read); the snapshot is what makes hybrid stitching pageable
- [[system-design-concepts/video-delivery-read-path]] — the feed-of-IDs budget this page owns, feeding the client's prefetch window
- [[system-design-concepts/read-state-watermarking]] — a related "where am I in a stream" anchor (per-user watermark for read state); cursor is its pagination cousin
- [[theory/consistency-models]] — snapshot isolation as a named guarantee; bounded staleness within a session is a deliberate consistency choice

## Sources
- [[sources/docs/design-youtube-shorts-self-interview]] — the cursor-stability crux: snapshot ID + COW-on-change + TTL, reached unprompted
- [self-youtube-shorts-design/](https://github.com/redblackcoder/interview-prep-raw/blob/master/docs/self-youtube-shorts-design/) — transcript + whiteboard
