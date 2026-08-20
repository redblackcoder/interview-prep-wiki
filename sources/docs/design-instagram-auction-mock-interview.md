---
source: docs/design-instagram-auction-mock-interview.md
source_url: https://github.com/redblackcoder/interview-prep-raw/blob/master/docs/design-instagram-auction-mock-interview.md
type: doc
date_extracted: 2026-08-20
topic: system-design-concepts
---

# Design Instagram Auctions — Mock Interview

Distilled from a Google **Senior Staff (L7)** ~40-min systems-design screen: **"Design an auction feature for Instagram"** — post an item, users bid, fixed end time, highest bidder at close wins. Feed/distribution and payments/shipping were out of scope, so the whole interview funneled onto one thing: **correctly serializing bids on a hot item under contention.** Interviewer's verdict: **lean hire, staff-level moments** — strong on the core, gap-to-senior-staff on self-critique cadence.

## Key Ideas

- **Aggregate load is a decoy; single-object contention is the real problem.** 100k bids/s spread over 700k live auctions is nothing — a fraction of a bid each, shardable on a laptop. The actual workload is **~10k bids/s hammering *one* auction** in the final seconds. You can shard aggregate throughput away by partitioning on `auction_id`; you **cannot** shard away contention on a single key, because by definition every bid on that item must agree on one outcome. Naming "hot-key write contention" as the crux is 50% of the question.
- **`max` is commutative, associative, and idempotent — so you don't need linearizability.** If the top is $100 and two people bid $101 and $102, $102 wins *regardless of ordering*. The final winner is invariant under bid order. What you actually need is **durability of every accepted bid** + **agreement on the cutoff (which bids are in-window)**. Ordering is irrelevant; **membership** is authoritative. Bonus: idempotent `max` makes at-least-once/duplicate delivery a non-issue.
- **Absorb single-key contention with a log, not a lock.** Don't compare-and-set the hot row. **Append every bid to an append-only log partitioned by `auction_id`**; batch heavily; a downstream **consumer computes the max over a trailing window and CAS-es the current top into Redis** (`auction_id → highest bid`). The live number is a *hint* fresh to ~100s of ms; the authoritative winner is recomputed from the durable ledger at close. Because `max` aggregates, a hot auction can even be **split across multiple partitions** (each consumer finds a local max → all fold into one Redis key).
- **PACELC beats CAP for everyday reasoning.** Partitions are rare (minutes/year). Contention on the hot item happens *always*. So the interesting axis isn't C-vs-A during a partition — it's the **E**lse branch: **latency vs. consistency** under a perfectly healthy network. That's where a contended system actually lives 99.9% of the time.
- **Deadline correctness is a clock-skew problem.** Display is skew-immune (clients read Redis). The hazard is the boundary: *did this bid land before close?* Server clocks disagree, so **accept bids in a small grace window (~50 ms) past local end** and let **recorded event timestamps** decide membership. The close daemon fires ~50 ms after end so the ± skew window is honored.
- **Consistency is a local dial, not a global constant.** A ~1s RPO on a rare region failure is fine for most of an auction's life. For the **last 30 seconds of a hot auction**, flip *that one auction* to **synchronous cross-region / quorum writes** and pay the latency only there, only then. Also: **in-region replication (Kafka RF=3) already survives an AZ loss for free** — only *region* loss needs cross-region machinery.
- **Never let the client be a source of truth.** My failover recovery idea (clients replay their last ~1s of acked bids) is a fine *best-effort hint*, but on an item-value/money-adjacent system a malicious client would fabricate/inflate bids exactly during failover chaos. Authoritative amounts must be **server-signed records**.

## My Understanding

- I got the crux right and fast, and I'm proud of the core mechanism: **log-append + deferred, batched, layered max-aggregation** is genuinely the right shape for single-key write contention, and exploiting the algebra of `max` (commutative/associative/idempotent) to spread one auction across partitions is the insight that makes it scale. The clock-skew-at-the-boundary handling (grace window + recorded timestamps) I reached unprompted, which the interviewer said most people never get to.
- My real failure was **conceptual self-consistency, not knowledge.** For fifteen minutes I loudly demanded *linearizability* and said I'd trade availability for it — then I designed an **eventually-consistent, optimistic** pipeline and didn't notice the contradiction until the interviewer forced it. The fix was in my head the whole time: I don't need ordering, I need durability + an agreed cutoff. At staff+, the bar is that **I interrogate my own requirements before the interviewer does** — I should have *derived* the consistency requirement from the math of the operation (`max` is order-free ⇒ eventual is fine), not asserted the strongest guarantee out of reflex. Over-claiming a guarantee is as much a red flag as under-specifying one.
- The second miss taught me the **"does this new box violate something I already committed to?" reflex.** I bolted on active/active dual-region failover at the end, and it silently reintroduced the **split-brain** I'd spent five minutes choosing CP to avoid — two regions see different bid streams ⇒ two divergent logs ⇒ dual leaders. The correct shape is single-leader with the **Kafka log as the replicated source of truth**, async cross-region (RPO ~1s), and I should have named the RPO out loud instead of pretending failover was free.
- The subtle thing I now really internalize: **"bid received" ≠ "bid adjudicated."** The instant I put a log/queue between the user and the max computation, I made bidding *asynchronous* — so the synchronous response can only be an ack, and "you're top bidder / you've been outbid" comes back later over the notification channel. That's an acceptable and even good UX, but I have to *say* it's async and name the return path, not quietly imply instant feedback.
- Numbers: this time the estimate was fine and I **caught my own nonsense** (700k auctions × 1000 bidders = 700M > 100M DAU) and reconciled it instead of defending it. That's the muscle I've been drilling; it held here.

## Interview-Craft Lessons

- **Interrogate your own requirements first.** Before asserting "this must be linearizable/strongly consistent," ask: *what does the operation's algebra actually need?* Commutative/associative/idempotent aggregates need durability + a cutoff, not ordering. Deriving the weakest sufficient guarantee is the staff move; reaching for the strongest one is a tell.
- **Run the violation check on every new box.** Whenever I add a component (especially failover/replication), spend 10 seconds: "does this contradict a property I already promised?" Active/active + "must be consistent" = split-brain, every time.
- **When you decouple, name the return path *and* the acknowledgment semantics.** A log/queue turns a sync call into "accepted now, resolved later." State that explicitly and say how the resolution reaches the exact user (here: notification/push, keyed by bid).
- **Turn consistency up locally, not globally.** Don't pay zero-RPO everywhere; pay it in the high-stakes window (auction's last 30s). Distinguish AZ loss (free via in-region RF) from region loss (needs cross-region).
- **Distrust the client for anything authoritative.** Client-side buffers/replay are hints; the server owns signed truth — especially during failover when defenses are weakest.
- **30-second answer for "how do you serialize thousands of bids on one item at ~100ms?":** "I don't serialize the writes — I append them to a per-auction log and defer the decision. `max` is order-independent, so I batch the log and compute the running max downstream into a cache for display, and recompute the authoritative winner from the durable log at close. The only things I truly need are that every accepted bid is durable and that everyone agrees on the deadline — not on the order."

## Open Questions

- **Exactly-once close:** the winner-declaration daemon must be idempotent and sharded — what's the actual mechanism so an auction is closed once and only once (fencing token on the close? conditional write of `winner` keyed by `auction_id`)? I left this as an open thread.
- **Skew-reconciliation algorithm at close:** I hand-waved "use the timestamps." Concretely, how do I bound cross-node clock skew (TrueTime-style uncertainty interval? bounded-NTP + margin?) and decide the in-window set deterministically when server clocks disagree?
- **Read fan-out for the live price:** millions may *watch* a hot auction's price tick without bidding. How do I fan out the current max (pub/sub to edge? push vs. poll?) without melting Redis — this is the read-side mirror of the write crux and I never designed it. *(Addressed post-interview → [[wiki/system-design-concepts/read-side-fanout]]: coalesce to latest + two-level fan-out over a pub/sub bus + snapshot/reconcile bootstrap.)*
- **Bid admission control / anti-spam:** best-effort reject-below-current-max at the edge using the (stale) Redis value to cut log spam, with the authoritative check at close — what's the false-reject risk when Redis is stale, and where does min-increment enforcement live?
- **Sync-window mechanics:** when I flip the last 30s of a hot auction to quorum writes, how does the write path *switch modes* mid-auction without dropping in-flight bids?

## Connections

- Builds directly on: [[sources/docs/how-to-beat-the-cap-theorem]] — this whole design is the **CALM / commutativity-beats-coordination** thesis in practice: an order-insensitive aggregate (`max`) needs no coordination, so it stays available and fast. The interview was a live application of "monotonic/commutative ⇒ coordination-free."
- Builds on: [[sources/docs/the-log-jay-kreps]] & [[wiki/system-design-concepts/the-log-abstraction]] / [[wiki/system-design-concepts/table-log-duality]] — the log is the source of truth; the Redis "current max" is a materialized view folded from it.
- Uses: [[wiki/tech/kafka]] — per-`auction_id` partitioning for the ledger, RF=3 for AZ survival, at-least-once made safe by idempotent `max`.
- Nuances: [[wiki/system-design-concepts/exactly-once-semantics]] — I *dodged* exactly-once on the bid path by choosing an idempotent aggregate; still needed for the close.
- Sharpens: [[wiki/theory/consistency-models]] — concrete case of "linearizability was overkill; eventual + agreed cutoff suffices"; and the **PACELC** framing (else-latency-vs-consistency) as the everyday axis over CAP.
- Same machinery as: [[sources/docs/streaming-101-world-beyond-batch]] / [[wiki/system-design-concepts/event-time-vs-processing-time]] — the trailing-1s consumer window + grace period at close *is* windowing with watermarks; the deadline is a watermark and the grace window handles late/skewed events.
- Relates to: [[wiki/theory/durability-rpo-rto]] — RPO ~1s async replication, the AZ-vs-region distinction, and turning durability up only in the final window.
- Return-path echo of: [[wiki/system-design-concepts/async-response-routing]] — "bid received" vs. adjudicated; feedback returns asynchronously and must be routed back to the bidder.
- Read-side twin: [[wiki/system-design-concepts/read-side-fanout]] — broadcasting the live max to millions of watchers (one→many), the mirror of the write-side hot-key crux.
- Shares the CP-under-partition stance with: [[sources/docs/distributed-kv-store-mock-interview]] — single-leader ownership, choosing consistency over availability for the contended key.
- Partitioning lens: [[wiki/system-design-concepts/hash-vs-range-partitioning]] — hash-by-`auction_id`, and why a hot key defeats naive partitioning.

## Concepts flagged for wiki (pending /update-wiki)
- **Hot-key write contention** — aggregate vs. single-object load are different problems; you can't shard away contention on one key. *(new)*
- **Commutative/idempotent aggregation over linearizability** — order-free aggregates (`max`/`sum`/set-union) need durability + a cutoff, not ordering; CALM/CRDT-adjacent; makes at-least-once safe. *(new)*
- **Log-structured writes to absorb contention** — append + deferred/batched/layered aggregation as an alternative to read-modify-write on a hot row. *(extend the-log-abstraction / table-log-duality)*
- **PACELC as the everyday frame** — else-latency-vs-consistency; partitions are the rare case. *(extend consistency-models)*
- **Deadline correctness under clock skew** — grace window + event timestamps; deadline-as-watermark. *(extend event-time-vs-processing-time)*
- **Consistency as a local dial** — sync/quorum only in the high-stakes window; AZ-loss ≠ region-loss. *(extend durability-rpo-rto)*
- **Client is never authoritative** — replay/buffers are hints; server-signed truth, especially during failover. *(new, small — or fold into a security concept)*
