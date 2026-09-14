---
source: docs/self-youtube-shorts-design/
source_url: https://github.com/redblackcoder/interview-prep-raw/blob/master/docs/self-youtube-shorts-design/
type: doc
date_extracted: 2026-08-21
topic: system-design-concepts
---

# Design YouTube Shorts — Self-Interview

A **self-driven** ~60-min systems-design run at "Design YouTube Shorts": record/upload a <30s video, have it appear in others' feeds within ~5 min, infinite-scroll a feed (algo + following), follow/unfollow. No interviewer — so the drill was as much about *self-critique cadence* as about the design. Self-graded **7/10 — strong senior, staff ceiling on the crux.** The two hardest feed insights (hybrid push/pull fan-out, snapshot-isolated cursor) were reached unprompted; the recurring tax (quantitative rigor, catching my own inconsistencies) showed up again, plus a genuinely thin read path.

## Key Ideas

- **The 1000:1 read:write ratio is the shaping constraint.** 1M uploads/day vs 1B watches/day. It justifies every downstream choice: multi-resolution transcode (spend once at write to save 1000× at read), CDN, read-replica cache tiers. Naming it early was the strongest quantitative move — numbers *shaped* the design there.
- **Upload path: proxy-through-a-service vs presigned direct-to-blob is a real fork.** I chose to route bytes through an internal storage-service abstraction (own the encoding pipeline, don't couple the client to S3). Defensible, but the scale-default is the *other* branch — **presigned-URL direct-to-blob + trigger the encoder off a storage event** — which kills the double-hop bandwidth I myself flagged as the con. Client coupling is fixable with my own signed-URL indirection. A strong interviewer pushes here.
- **Materialized feed of IDs, hydrated at read.** `user_id → [post_id]` (a sorted set scored by recency for the following feed, by model score for the algo feed), one interface over both paths so features don't cost 2×. Feed stores only tiny IDs; metadata + CDN URLs are hydrated on read. Orchestrator (Temporal-style durable workflow) drives upload→encode→fan-out with checkpoint/resume.
- **The crux (nailed): fan-out can't be pure push.** Celebrity/viral upload ⇒ millions of feed writes ⇒ can't hit the 5-min SLA. Resolution = **hybrid**: fan-out-on-write for the masses, **don't** fan out hot users — stitch their posts in at read time from a per-user posts table and cache the unified feed. → [[wiki/system-design-concepts/timeline-fanout-hybrid]].
- **The crux (nailed): cursor stability under a mutating feed.** Followers change mid-scroll ⇒ items shift/dupe/skip. Resolution = **snapshot isolation**: a snapshot ID pins a feed version, the generator won't overwrite an in-use snapshot (copy-on-write a new version), snapshots are TTL'd. → [[wiki/system-design-concepts/feed-cursor-stability]].
- **The hot video path is a *serving* problem, not a feed-cache problem — but there are three hot spots, not one.** The feed cache is keyed per-user, so virality spreads across the keyspace (no hot key). The real pressure lands on: **(1) video bytes → CDN** (dominant), **(2) the one viral video's metadata → hot *read* key**, **(3) its view/like counter → hot *write* key**. Only #1 is the CDN's job; #2/#3 live in my services and need their own treatment. → [[wiki/tech/cdn]], [[wiki/system-design-concepts/hot-key-write-contention]].
- **A viral video is the CDN's best case, not worst case.** High popularity = high edge cache-hit ratio = origin barely touched. The CDN's *hard* case is the long tail of rarely-watched videos. The scary moment for a hot object is the **cold/expiry instant** (synchronized miss ⇒ origin stampede), solved by request coalescing + shield + stale-while-revalidate. → [[wiki/system-design-concepts/cache-stampede]].

## My Understanding

- I'm most proud that I reached the **two feed insights on my own**: the push/pull hybrid (treat a viral-via-algo video exactly like a celebrity — don't write it to a million feeds, stitch it at read) and the **snapshot-isolated cursor** (most people never even see that a mutating feed corrupts pagination). Those are the money moments in a feed design and they're the reason the ceiling is staff.
- My mental model of the **hot video path was directionally right but incomplete.** I correctly said "virality is about serving the bytes from cache, the feed cache isn't the pressure point" — and the *reason* is that the feed cache is keyed per-user, so the same viral `post_id` scatters across millions of distinct keys and never becomes a hot key. What I *missed* is that the viral object still creates two non-CDN hot spots: the **metadata for that one `post_id` is a hot read key** (every feed hydration fetches it) and the **view counter is a hot write key** (every view increments it — which is literally the Instagram-auction hot-key insight I already have). The CDN saves the bytes; it does nothing for those two. Fix: replicate/edge-cache the metadata, and shard/approximate the counter.
- I now understand the **CDN as just a distributed reverse-proxy cache + routing**, and that the properties I care about fall out of that: route the user to the nearest PoP (anycast or DNS mapping), serve on HIT, on MISS go edge→regional shield→origin so origin load is O(#shields) not O(#edges). **Cache-hit ratio is mostly *my* job, not the CDN's**: make video URLs immutable + versioned (a finished encode never changes, so TTL≈∞, no revalidation), and **segment the video (HLS/DASH)** so one 4s chunk is a single cache key shared by every viewer who reaches that point. The long tail is what tanks hit ratio; a shield + cache-admission ("cache on 2nd hit") handles it.
- The read-path ding landed: I **asserted <100ms playback and then never built it.** The mechanism is **client prefetch** — the client already holds the next N videos' first segments before the swipe — plus **adaptive bitrate** so the player fetches a small segment, not a whole file, and picks a rendition to fit the device/network. The <100ms SLA is really a **CDN cache-hit-ratio** target, not a single-service latency. I named the mechanism ("push to client") in one breath and dropped it; naming ≠ designing.
- The self-critique gap is the same pattern as the last three interviews and it's now undeniable: I let a **10× peak error ride** (said "100× of 10⁴ = 10⁶" out loud, then used 10⁷ on the board and in sizing, and quoted "10 read replicas" which only works at 10⁶), and I **never quantified bandwidth/storage** — the one number that matters for a *video* system and the actual justification for the CDN. With no interviewer to catch these, they survived the whole hour. That's the drill I keep failing: numbers must *drive and reconcile*, and I have to run the "does this contradict something I just said?" check on myself.

## Interview-Craft Lessons

- **Pin the peak once, then make every capacity claim divide into it.** 100× of 10⁴ = 10⁶/s. If I'd done the replica math honestly against a fixed peak, the stray 10⁷ would have fallen out immediately. An unreconciled number is a landmine an interviewer will step on.
- **On any media system, size egress/storage explicitly.** bytes/video × renditions × 10⁹ watches/day *is* the CDN's justification and the dominant cost line. Qualitative "CDN saves bandwidth" is not the same as the number.
- **Engineer the SLA you assert.** <100ms ⇒ say "client prefetches next-N segments; the budget is a CDN hit-ratio target; a miss costs an origin/shield RTT." Don't state a latency you haven't mechanized.
- **Enumerate hot spots by layer, not by reflex.** A viral object is hot at the bytes (CDN), the metadata (hot read key), and the counter (hot write key). "The CDN handles it" only covers one third.
- **Interrogate my own choice.** I named the bandwidth con of proxying uploads, then chose proxying — the 10-second check ("does this contradict a cost I just stated?") would have forced me to at least defend it out loud.
- **30-second answer for "how does a viral Short stay fast?":** "It's a serving-not-feed problem — the feed cache is keyed per user so virality never makes a hot feed key. The bytes are served by the CDN, and virality is actually the CDN's easy case: high popularity means high edge hit-ratio. I make that work by giving each video an immutable, segmented URL so a 4-second chunk is one cache key shared by everyone. The real risks are the cold/expiry stampede on the hot object — handled by request coalescing + an origin shield + stale-while-revalidate — and the two non-CDN hot spots virality creates: the video's metadata becomes a hot *read* key (replicate/edge-cache it) and its view counter a hot *write* key (shard or approximate it)."

## Open Questions

- **Cold-start feed** for a brand-new user with no follows and no interaction history — what seeds the algo feed?
- **Already-seen dedup** across an infinite scroll session (and across devices) — where does the seen-set live, and how big before it's a cost?
- **Pure-pull for the following feed at low follow-counts** — I defaulted both feeds to materialization; when is query-followed-users'-recent-posts-and-merge actually cheaper than maintaining a materialized list?
- **Counter exactness vs cost** for view counts — approximate (sharded/lazy sum) is fine for display, but monetization/analytics may need exact; where's the split and the reconcile path?
- **Serving-path failure modes I didn't design** — shield failover, CDN region outage, cache penetration by garbage keys, and the metadata hot-read-key mitigation concretely (local cache in feed service? dedicated replica set?).

## Connections

- Extends / is the feed sibling of: [[wiki/system-design-concepts/message-fanout]] (real-time pub/sub fan-out) and [[wiki/system-design-concepts/read-side-fanout]] (one→many live updates) — the timeline push/pull hybrid is the *third* fan-out shape (precompute-on-write vs compute-on-read), distinct from both. → [[wiki/system-design-concepts/timeline-fanout-hybrid]].
- Reuses: [[wiki/system-design-concepts/hot-key-write-contention]] — the viral view-counter is the same hot-write-key crux from the auction round; the viral metadata is its hot-*read* mirror.
- Builds on: [[wiki/theory/copy-on-write-vs-mvcc]] — the snapshot-isolated cursor is COW/MVCC applied to a feed; [[wiki/system-design-concepts/feed-cursor-stability]] is the applied page.
- New serving machinery: [[wiki/tech/cdn]], [[wiki/system-design-concepts/cache-stampede]], [[wiki/system-design-concepts/video-delivery-read-path]].
- Latency budgets: [[wiki/theory/latency-numbers]] — the two-tier read budget (feed-of-IDs vs bytes) and why prefetch beats a per-swipe fetch.
- Durability/ops echo of: [[wiki/theory/durability-rpo-rto]] — the cleanup-validates-before-delete + orchestrator-resume instinct on the upload path.
- Same recurring self-critique gap as: [[sources/docs/design-instagram-auction-mock-interview]], [[sources/docs/design-chatgpt-mock-interview]] — numbers must drive; catch your own contradictions.

## Concepts flagged for wiki (done in this /update-wiki)
- **Timeline fan-out hybrid** — fan-out-on-write vs -on-read; celebrity problem; SLA forces the split. *(new)*
- **Feed cursor stability** — snapshot isolation over a mutating feed; why offset pagination corrupts. *(new)*
- **CDN** — routing, edge→shield→origin, immutability + segmentation for hit ratio, viral = best case. *(new, tech)*
- **Cache stampede / thundering herd** — coalescing, stale-while-revalidate, shield, pre-warm. *(new)*
- **Video delivery read path** — client prefetch + adaptive bitrate + segmentation as latency *and* cache lever. *(new)*
- **Hot read key** — viral object's metadata read by millions; extend [[wiki/system-design-concepts/hot-key-write-contention]]. *(extend)*
