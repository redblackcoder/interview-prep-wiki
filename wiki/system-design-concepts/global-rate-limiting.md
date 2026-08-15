# Global / Multi-Region Rate Limiting

The hardest form of the [[system-design-concepts/rate-limiting|rate-limiting]] question: *"Rate-limit a globally-distributed API (e.g. Twitter) by API key, so a user can't bypass the limit by VPN-hopping across regions — without a globally-consistent Redis."* It sounds like a hard consistency problem. It mostly isn't, once two framings are right.

## Framing 1 — VPN-hopping is beaten by identity keying, not counter consistency
The bypass exists **only if the counter is partitioned by geography** (independent per-region limiters with no cross-region accounting). Key the counter on **identity** (the API key) instead, map that key to *one logical global count*, and hopping us→eu→asia draws down the *same* total everywhere. For an authenticated API the identity is the credential (see [[system-design-concepts/client-identification]]) — a VPN changes the IP, never the token. So "make the global counter strongly consistent so hopping can't help" solves the wrong problem with the most expensive tool.

## Framing 2 — globally *consistent* ≠ globally *accurate*
- **Consistent** (linearizable) — expensive, unnecessary.
- **Accurate** (eventually convergent) — cheap, sufficient.

Ask the business question: if the limit is 1000/hr, does anything break if an attacker briefly gets 1010 before regions converge? Almost never. **The first design move is choosing the consistency model — "bounded overshoot is fine" — not choosing a store.** That choice dissolves the latency problem.

## The pushback, made precise
A *synchronous* global counter on the hot path is genuinely non-viable — but because of **cross-region RTT** (us↔eu ≈ 80 ms, us↔asia ≈ 150–250 ms), not because of Redis. Adding that to every single-digit-ms API call is a non-starter, and it makes availability *worse* (every region depends on a far quorum).

Note the near-**category error**: "globally consistent Redis" doesn't really exist — Redis Cluster replicates *asynchronously* and does no cross-region consensus. So reject the synchronous global counter — but for the RTT reason. An *eventually-consistent* global Redis (active-active) very much **is** viable.

## Three viable designs — no request-path RTT

| Approach | How it works | Overshoot | Cost |
|---|---|---|---|
| **CRDT counters (active-active)** | Each region increments its own sub-counter locally; regions async-replicate and **merge by summing per-region counts** (a PN/G-Counter — the canonical CRDT). Never loses a count; converges to the true total. Redis Enterprise "Active-Active" ships this. | ~ replication lag (sub-sec–secs) | Local write latency; async bandwidth |
| **Home-region pinning** | `hash(api_key) → home region`. All counting for a key lives in its home Redis; out-of-home requests forward *just the rate-limit check* home. | ~0 (one authoritative counter) | One cross-region hop for the fraction of a key's traffic outside home |
| **Local budget + async reconcile** | A central controller hands each region a *share* of the global budget from observed demand; regions enforce locally; the controller redistributes on a slow loop (Google "Doorman"-style / large-CDN pattern). | Bounded by share granularity + reconcile interval | Fast local check; global math off-path |

## Recommended architecture — tier consistency by the cost of overshoot
- **Hot path is always local** — in-region Redis, or an in-process token bucket for the very hottest keys (tier-1 from the local/global framing).
- **Most limits** (throughput/fairness, where +5% overshoot is harmless) use **CRDT active-active** — zero request-path RTT, tiny bounded overshoot.
- **A few security-critical, low-volume limits** ("10 password resets/hr/account") that must be *tight* use **home-region pinning** — accept one cross-region hop because volume is low and correctness matters.

Different consistency for different limits, justified by blast radius — *that* distinction is the staff-level signal. This is the multi-region extension of the "push to per-node budgets with async reconciliation" scaling move in [[system-design-concepts/rate-limiting]].

## Key points
- VPN/geo-hopping is defeated by **keying on identity**, not by strengthening the counter — a partitioned-by-geography counter was the only thing ever bypassable.
- Distinguish **consistent** (linearizable, expensive, unneeded) from **accurate** (eventually convergent, cheap, sufficient); rate limiting wants the latter.
- A synchronous global counter fails on **cross-region RTT**, not on Redis; "globally consistent Redis" is asynchronous anyway.
- CRDT active-active, home-region pinning, and local-budget-reconcile all give global accuracy with no hot-path RTT.
- Tier the consistency model per limit by the cost of overshoot.

## Interview angle

> "First I'd separate two things people conflate: globally *consistent* versus globally *accurate*. Rate limiting only needs accurate — bounded overshoot is fine, so I never pay for linearizable cross-region consensus, which would add 80–250 ms to every call anyway. Second, VPN-hopping only works if the counter is partitioned by geography; I key on the API key and map it to one logical global count, so hopping draws down the same total. Concretely: hot path stays local, most limits use CRDT active-active counters that async-merge by summing per-region sub-counts, and the few tight security limits use home-region pinning at the cost of one cross-region hop. It's CAP applied to counting — relax exactness to bounded overshoot, the dimension the business cares least about."

## Connections
- [[system-design-concepts/rate-limiting]] — the base framing; this page is its multi-region / distributed-accuracy extension
- [[system-design-concepts/client-identification]] — why an authenticated API key defeats VPN-hopping regardless of geography
- [[tech/envoy-ratelimit-service]] — the single-region RLS whose Redis counter this scales across regions
- [[theory/consistent-hashing]] — the `hash(key) → home region` mechanism behind home-region pinning
- [[tech/aws-elasticache-redis]] — async replication semantics; why cross-region Redis is eventually consistent, not linearizable
- [[system-design-concepts/work-distribution]] — local-budget-reconcile is the same "hand out shares, reconcile centrally" pattern

## Sources
- [[sources/docs/rate-limiting-study-guide]] — §7.5 global / multi-region rate limiting
- [rate-limiting-study-guide.html](https://github.com/redblackcoder/interview-prep-raw/blob/main/docs/rate-limiting-study-guide.html#global-rl) — consistency spectrum, CRDT/home-region/reconcile table, CAP-applied-to-counting
