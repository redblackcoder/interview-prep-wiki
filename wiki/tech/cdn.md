# CDN (Content Delivery Network)

A CDN is a fleet of **reverse-proxy caches (PoPs / edge servers)** placed physically close to users, sitting in front of your origin. Every "magic" property — low latency, origin offload, DDoS absorption, surviving a viral spike — falls out of that one sentence: *distributed cache + smart routing*. For a media system (video, images) it's not an optimization, it's the serving tier: with a 1000:1 read:write ratio, you cannot and should not serve the bytes from your own fleet.

## The one-sentence mental model

> **A CDN turns "millions of users pull one object from my origin" into "millions pull from a nearby cache that already has it." Your job is to make the object trivially cacheable (immutable, segmented URLs) and to protect the origin during the one dangerous moment — the cold/expiry miss. A viral object is the *easy* case; the long tail is the hard one.**

## Getting the user to the nearest edge

Two routing mechanisms (providers use one or both):
- **Anycast** — the same IP is announced from every PoP; BGP routes the packet to the nearest one (Cloudflare-style).
- **DNS-based mapping** — your hostname CNAMEs to the CDN, whose DNS returns the closest edge IP (Akamai-style, using EDNS client-subnet to locate the real user).

Either way the client talks to a nearby edge that **terminates TLS** and keeps warm, pooled connections to origin — so you save handshake RTTs *even on a miss*.

## The request lifecycle (the whole game)

```
client → nearest edge PoP
          ├─ HIT  → serve from edge RAM/SSD          (fast; origin untouched)
          └─ MISS → regional "shield" / mid-tier PoP
                     ├─ HIT  → fill edge, serve
                     └─ MISS → origin (your S3/blob) → fill shield+edge → serve
```

The **origin shield / mid-tier** (Akamai "origin shield", Cloudflare "tiered cache") is the underrated piece: N edges don't each hit origin; they hit one regional shield, and only the shield's miss reaches origin. Origin load drops from `O(#edges)` to `O(#shields)`.

## The levers YOU control (via response headers)

The cache key is essentially the **URL** (± selected headers/query). Origin controls caching with:
- `Cache-Control: max-age=31536000, immutable` — cache a year, never revalidate.
- `ETag` / `Last-Modified` — cheap revalidation (`304 Not Modified`) when TTL lapses.
- `stale-while-revalidate` / `stale-if-error` — serve slightly stale bytes *immediately* while refreshing in the background, and keep serving stale if origin is down (an availability win, and a stampede defense).

## Push vs pull

- **Pull (origin-pull, lazy)** — edge fetches from origin on first miss. Default for UGC: you have millions of videos, most watched rarely; you can't pre-position everything.
- **Push (pre-warm)** — proactively load content to edges *before* demand. Use for predictable spikes (a premiere, a creator you know will trend).

## Maximizing cache-hit ratio

Hit ratio ≈ **content popularity (Zipfian) × how cacheable you made the URLs.**

**Make the content maximally cacheable (mostly your job):**
- **Immutable, versioned / content-addressed URLs.** A finished video encode never changes, so `max-age` can be effectively infinite → zero revalidation traffic. Never cache-bust with random query strings; normalize URLs so the same object = the same key.
- **Segment the video (HLS/DASH).** The single biggest lever: a ~2–6s segment is an immutable object requested by *every* viewer who reaches that point — millions of requests collapse onto **one cache key**. (See [[system-design-concepts/video-delivery-read-path]].)

**Fight the long tail (this is what actually tanks hit ratio):**
- The hot video is the *easy* case (popularity → near-100% hit → origin idle). The killer is the millions of rarely-watched videos whose misses all reach origin.
- **Tiered caching / shield** collapses tail misses.
- **Cache-admission policy** ("cache on the *2nd* hit", TinyLFU/2Q) stops one-hit-wonders from evicting genuinely hot objects.

## Surviving the viral spike (cache stampede)

The danger is not steady viral traffic (that's a near-100% hit) — it's the **synchronized miss**: a hot object is cold (never fetched) or its TTL just expired, and thousands of edge requests miss *at once* and stampede origin.
- **Request coalescing / collapsed forwarding** — the edge sends **one** origin request and queues the rest behind it (nginx `proxy_cache_lock`, Varnish coalescing). Origin sees 1, not 10⁶.
- **stale-while-revalidate** so expiry never triggers a synchronized origin fetch.
- **Pre-warm** if the spike is predictable.

Full treatment: [[system-design-concepts/cache-stampede]].

## What a CDN does *not* solve

A CDN caches the **bytes**. Virality also makes the viral object's **metadata a hot read key** (every feed hydration reads it) and its **view counter a hot write key** (every view increments it) — both live in *your* services, not the edge. Handle those separately (replicate/edge-cache metadata; shard/approximate the counter). See [[system-design-concepts/hot-key-write-contention]].

## Key points

- CDN = distributed reverse-proxy cache + routing to the nearest PoP; TLS terminates at the edge, so even a miss saves handshake RTTs.
- edge → regional shield → origin; the shield cuts origin load from `O(#edges)` to `O(#shields)`.
- You set cacheability via headers; **immutable + segmented URLs** are the hit-ratio superpower for video.
- Hit ratio is governed by popularity × cacheability; the **long tail**, not the hot object, is the hard problem.
- **Viral = best case** for a CDN; the only scary moment is the cold/expiry miss → coalescing + shield + stale-while-revalidate.
- The CDN saves the bytes only — metadata and counters remain your hot keys.

## Interview angle

> "A CDN is just a fleet of reverse-proxy caches near users, plus routing to the nearest one. On a hit it serves locally; on a miss it goes edge → regional shield → origin, so origin sees O(number of shields), not O(number of edges). For video the whole game is cacheability: I give each encode an immutable, segmented URL — HLS/DASH chunks — so a 4-second segment is one cache key shared by every viewer at that point, and I can set effectively-infinite TTL because a finished encode never changes. Counterintuitively a viral video is the CDN's *easy* case: high popularity means high hit ratio and origin is idle. The hard case is the long tail of rarely-watched videos, which I handle with a shield tier and cache-on-2nd-hit admission. The one dangerous moment is a synchronized miss when a hot object is cold or its TTL expires — I defend that with request coalescing, an origin shield, and stale-while-revalidate. And I'm explicit that the CDN only saves the *bytes* — the viral video's metadata is still a hot read key and its view counter a hot write key, which I solve in my own services."

## Connections
- [[system-design-concepts/video-delivery-read-path]] — segmentation + adaptive bitrate + client prefetch; segmentation is the CDN hit-ratio lever *and* the startup-latency lever
- [[system-design-concepts/cache-stampede]] — the cold/expiry synchronized-miss failure and its defenses (coalescing, stale-while-revalidate, shield, pre-warm)
- [[system-design-concepts/hot-key-write-contention]] — what the CDN does NOT save: viral metadata (hot read key) + view counter (hot write key)
- [[system-design-concepts/timeline-fanout-hybrid]] — the feed returns CDN URLs for post IDs; the bytes never touch the feed path
- [[theory/latency-numbers]] — why an edge hit (LAN/metro RTT) beats an origin fetch (cross-WAN) for a <100ms budget
- [[system-design-concepts/read-side-fanout]] — the other read-scaling shape (live one→many push); a CDN is read-scaling for *static* bytes
- [[tech/https-tls]] — TLS termination at the edge; connection reuse to origin

## Sources
- [[sources/docs/design-youtube-shorts-self-interview]] — the hot-video serving path; CDN crash course prompted by a thin CDN treatment in the run
- [self-youtube-shorts-design/](https://github.com/redblackcoder/interview-prep-raw/blob/master/docs/self-youtube-shorts-design/) — transcript + whiteboard
