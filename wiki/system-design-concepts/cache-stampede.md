# Cache Stampede (Thundering Herd on a Hot Object)

A cache absorbs read load beautifully — until the moment a **hot object is absent**: it was never fetched (cold), or its TTL just expired. Then many concurrent requests miss *simultaneously* and all fall through to the origin at once — a **stampede** (a.k.a. dogpile / thundering herd) that can knock over the very backend the cache was protecting. It is the dangerous instant hiding inside an otherwise-easy hot-read path (a viral video segment on a CDN, a hot key in Redis, a rendered page).

## The one-sentence mental model

> **Steady popular traffic is a near-100% hit and trivial; the risk is the *synchronized miss*. Collapse the concurrent misses into one origin fetch (coalescing), avoid the synchronized expiry (serve stale while you refresh), and shield the origin behind a mid-tier — so the backend sees one request, not a million.**

## When it fires

- **Cold start / cache flush** — a deploy, failover, or eviction empties the cache and a popular key is suddenly a miss everywhere at once.
- **Synchronized TTL expiry** — many replicas cached the same object with the same TTL; they all expire in the same second and miss together.
- **Newly viral object** — a video segment nobody had requested trends in minutes; the *first* wave at each edge is a coordinated miss before the cache fills. (This is the CDN-specific case — see [[tech/cdn]].)

The tell: origin QPS is fine at steady state, then spikes to `O(concurrent readers)` for one key at the miss instant.

## The defenses (compose them)

1. **Request coalescing / collapsed forwarding.** On a miss, the cache issues **one** origin fetch and **queues** all other concurrent requests for that key behind it; when the fill returns, everyone is served. Origin sees 1, not N. (nginx `proxy_cache_lock`, Varnish request coalescing, CDN "concurrent streaming acceleration"; in-app: a per-key in-flight `Future`/singleflight, or a short-lived lock.)
2. **Serve stale while revalidating.** `stale-while-revalidate` (HTTP, or app-level): on expiry, **serve the stale value immediately** and refresh **asynchronously** in the background, so expiry never causes a synchronized origin fetch. `stale-if-error` extends this to origin outages (availability win).
3. **Tiered / shield origin.** Edges pull from a **regional shield**, the shield from origin — so even uncoalesced misses fan into `O(#shields)` origin hits, not `O(#edges)`.
4. **Jittered / probabilistic early expiry.** Add random jitter to TTLs so replicas don't expire in lockstep; or refresh probabilistically *before* expiry (XFetch-style: as TTL approaches, one request volunteers to recompute early) so there's never a hard cliff.
5. **Pre-warm the predictable spike.** Push known-hot content (a premiere, a scheduled drop) to caches before the traffic arrives, turning the first wave into hits.

## Relation to load-shedding

Coalescing/stale-serving fix a **read** stampede (many readers, one value). If the herd is instead many *distinct* doomed requests (a reconnection storm), you want **admission control / a semaphore** to shed before the doomed call — see the Discord semaphore in [[system-design-concepts/message-fanout]] and [[system-design-concepts/rate-limiting]]. Same "thundering herd" word, different lever: coalesce identical work vs. shed excess distinct work.

## Key points
- The stampede fires on the **synchronized miss** (cold, expiry-in-lockstep, or newly viral), not on steady popular traffic.
- **Coalescing** turns N concurrent misses on one key into a single origin fetch — the primary defense.
- **stale-while-revalidate** removes the synchronized-expiry cliff by decoupling "serve" from "refresh."
- **Shield tier** bounds origin fan-in; **TTL jitter / early recompute** de-synchronizes expiry; **pre-warm** handles predictable spikes.
- Distinguish from **load-shedding**: coalesce *identical* work; shed *excess distinct* work.

## Interview angle

> "A cache is great until a hot object goes missing — cold cache, or a TTL that expired everywhere at once — because then every reader misses simultaneously and stampedes the origin the cache was supposed to protect. The steady viral case is actually fine, it's a near-100% hit; the danger is the synchronized miss. Primary defense is request coalescing: on a miss the cache sends one origin fetch and queues the rest behind it, so origin sees one request not a million. I pair that with stale-while-revalidate so expiry serves the stale value and refreshes in the background instead of triggering a synchronized fetch, a shield tier so uncoalesced misses fan into a handful of origin hits, and TTL jitter so replicas don't expire in lockstep. For a predictable spike I pre-warm. If instead the herd were many *distinct* doomed requests — a reconnection storm — that's a load-shedding/semaphore problem, not a coalescing one."

## Connections
- [[tech/cdn]] — the newly-viral segment is the edge stampede case; shield + coalescing + stale-while-revalidate are CDN features
- [[system-design-concepts/video-delivery-read-path]] — a suddenly-hot video segment is exactly the cold-object miss at the edge
- [[system-design-concepts/hot-key-write-contention]] — the write-side twin: this is the *read*-side herd on one key; both are "one key, everyone at once"
- [[system-design-concepts/message-fanout]] — the semaphore/back-pressure lever for the *distinct-request* herd (reconnection storm), contrasted with coalescing
- [[system-design-concepts/rate-limiting]] — admission control as the shed-excess-work sibling of coalesce-identical-work
- [[tech/aws-elasticache-redis]] — where an app-level singleflight/lock + jittered TTL live for a Redis hot key

## Sources
- [[sources/docs/design-youtube-shorts-self-interview]] — serving-path failure modes for a viral video; the cold/expiry stampede and its defenses
- [self-youtube-shorts-design/](https://github.com/redblackcoder/interview-prep-raw/blob/master/docs/self-youtube-shorts-design/) — transcript + whiteboard
