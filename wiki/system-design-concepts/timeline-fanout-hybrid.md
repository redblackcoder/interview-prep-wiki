# Timeline Fan-out: Push vs Pull vs Hybrid

How a social feed/timeline gets *built*: when A posts, whose feeds does it enter, and *when* — at write time (precompute into every follower's feed) or at read time (gather followed users' recent posts on request)? This is the defining architecture decision for Twitter/Instagram/YouTube-Shorts-style feeds, and the answer at scale is always **hybrid**, because a celebrity breaks pure push and a normal read pattern breaks pure pull.

## The one-sentence mental model

> **Fan-out-on-write (push) makes reads cheap and writes expensive; fan-out-on-read (pull) makes writes cheap and reads expensive. A celebrity's post is a million writes (push dies) and a user follows hundreds (pull dies), so you push for the masses and pull-then-stitch for the hot minority — the freshness SLA is what forces the split.**

## The two pure strategies

**Fan-out-on-write (push / precompute).** On publish, append the `post_id` into every follower's materialized feed (`user_id → [post_id]`, a sorted set scored by recency or model score). Reads are a cheap range scan of one key.
- ✅ O(1)-ish, cache-friendly reads; the read path is trivial.
- ❌ A write costs O(followers). A celebrity with 100M followers = 100M feed writes per post — can't meet a 5-min freshness SLA, and wastes work on inactive followers.

**Fan-out-on-read (pull / compute-on-read).** Store each user's own posts (`user_id → [post_id]`); at read time, gather the recent posts of everyone they follow and merge-sort.
- ✅ O(1) writes; nothing wasted on inactive users; always fresh.
- ❌ A read costs O(followed × their posts) + a merge, on the hottest path in the system. Falls apart for high-follow users and high read QPS.

## The hybrid (what real systems do)

- **Push for ordinary authors** — most users have modest follower counts, so write amplification is bounded and reads stay cheap.
- **Pull for celebrities / hot posts** — do **not** fan a viral or celebrity post into millions of feeds. Keep it in a per-author posts table and **stitch it in at read time**, merging with the reader's materialized feed, then cache the unified result so the stitch runs once per (user, snapshot), not once per page.
- **The threshold is the freshness SLA**, not a magic follower number: fan-out-on-write is only viable while `followers / write_throughput ≤ SLA`. Above that, the post must be pulled.
- **A viral-via-algorithm post is the same case as a celebrity.** If the ranking model would inject one video into millions of feeds, treat it like a celebrity — pull-and-stitch, don't push. This is why the algo and following feeds share one fan-out interface.

## Interaction with the rest of the feed

- **The feed holds only IDs.** Push/pull decides *which* `post_id`s are in a feed; hydration (metadata, [[tech/cdn]] URLs) happens at read regardless. Keeps the materialized feed tiny.
- **Cursor stability is orthogonal but coupled.** Stitching hot posts at read time changes results as follows change; pin a snapshot so pagination stays stable → [[system-design-concepts/feed-cursor-stability]].
- **Durability of the fan-out.** A million-write fan-out is a long job — run it on a durable orchestrator (batch followers, checkpoint, resume) so a mid-fan-out failure doesn't drop or double feed entries.

## Key points

- Push = cheap reads / expensive writes; pull = cheap writes / expensive reads. Neither survives alone at scale.
- The killer for push is the **celebrity write amplification**; the killer for pull is **high-follow / high-QPS reads**.
- Hybrid: push the masses, **pull-and-stitch the hot minority**, cache the merged feed.
- The **freshness SLA sets the threshold** for flipping an author from push to pull — reason from `followers/throughput ≤ SLA`, not a hardcoded number.
- A viral algo-boosted post = a celebrity for fan-out purposes; one interface serves both feeds.
- Materialized feeds store **IDs only**; hydrate + attach CDN URLs on read.

## Interview angle

> "Fan-out on write precomputes each post into every follower's feed — reads are a cheap scan but a write is O(followers), which explodes for a celebrity. Fan-out on read gathers followed users' posts at request time — writes are free but reads are O(followed), which explodes for high-follow users under high QPS. So I go hybrid: push for ordinary authors, and for celebrities or algorithmically-boosted viral posts I *don't* fan out — I keep them in a per-author table and stitch them into the feed at read time, then cache the merged result so the stitch is amortized. The threshold for flipping an author to pull isn't a magic follower count, it's the freshness SLA: push is only viable while followers over write-throughput stays under the SLA. The feed itself stores only post IDs; metadata and CDN URLs are hydrated on read, and I pin a snapshot so the cursor stays stable while follows change underneath."

## Connections
- [[system-design-concepts/message-fanout]] — a *different* fan-out: real-time pub/sub delivery to connected sockets (Discord Manifold), not timeline precompute. Same word, different problem.
- [[system-design-concepts/read-side-fanout]] — another distinct fan-out: one→many *live* updates (a ticking value) coalesced over a bus; this page is precompute-vs-compute for a *stored* feed
- [[system-design-concepts/feed-cursor-stability]] — pairs with hybrid fan-out: stitching-at-read plus mutating follows requires a pinned snapshot to page safely
- [[system-design-concepts/hot-key-write-contention]] — the celebrity is the write-amplification cousin of a hot key; pulling-instead-of-pushing is "don't do the O(N) work on the hot path"
- [[tech/cdn]] — the feed returns CDN URLs for the hydrated posts; bytes never traverse the fan-out path
- [[system-design-concepts/the-log-abstraction]] — the durable orchestrated fan-out is a log-driven, resumable projection into per-user feed views
- [[theory/latency-numbers]] — why precomputing reads is worth expensive writes when reads outnumber writes ~1000:1

## Sources
- [[sources/docs/design-youtube-shorts-self-interview]] — the fan-out crux: celebrity push can't meet the 5-min SLA → pull-and-stitch hybrid, reached unprompted
- [self-youtube-shorts-design/](https://github.com/redblackcoder/interview-prep-raw/blob/master/docs/self-youtube-shorts-design/) — transcript + whiteboard
