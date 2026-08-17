# Read Repair

The **read-time** convergence mechanism in a leaderless, quorum-replicated store: when a read touches multiple replicas and finds them disagreeing, the coordinator returns the newest version to the client **and** writes it back to the stale replicas. It's the cheapest way to converge, because it piggybacks on work you were already doing — a read — and it self-prioritizes the data that matters most: whatever is being read.

## The one-sentence mental model

> **On a quorum read, if replicas disagree, return the winner to the client and asynchronously push it back to the losers — repair rides along on reads, so hot keys heal themselves for free.**

## Where it sits

Hinted handoff and sloppy quorum leave replicas **divergent** (a write landed on a stand-in, or a replica missed a write while briefly down). Something must reconcile them. Read repair is the leg that fixes **keys that get read** — the hot set — at essentially no extra cost:

| Mechanism | Trigger | Covers |
|---|---|---|
| [[system-design-concepts/hinted-handoff]] | write, replica down | keeps the write alive |
| **Read repair** (this page) | read finds divergence | **hot keys** (being read) |
| [[system-design-concepts/anti-entropy-merkle-trees]] | background scan | **cold keys** (never read) |

The division is the insight: read repair covers hot data cheaply, anti-entropy sweeps the cold tail; together they cover everything.

## How it works

A read with `R > 1` contacts multiple replicas and compares their versions (via timestamp for last-write-wins, or **version vectors** to detect true conflicts — see below):

```
 read k, R=2 of RF=3:
   N1 → v7 (newest)
   N2 → v5 (stale — missed a write / had a hint elsewhere)
   coordinator picks v7  ─▶ returns v7 to client
                          └─▶ async: write v7 to N2  (the repair)
   next read of k sees N1,N2 agree.
```

### Foreground vs background repair

- **Blocking / foreground:** repair the divergent replicas *before* returning to the client. Stronger convergence (helps monotonic-read guarantees) but adds the write-back to read latency.
- **Async / background:** return immediately, repair off the critical path. Standard for latency-sensitive reads; the client's own read isn't slowed.

### Read repair alone can't cover everything

It only touches keys someone reads. A key written, never read, and sitting stale on a replica is **invisible to read repair forever** — which is precisely the gap [[system-design-concepts/anti-entropy-merkle-trees]] exists to close. Cassandra also offers **probabilistic read repair** (`read_repair_chance`): on reads that only needed one replica, occasionally query the others anyway to seed convergence on warm-but-not-hot keys.

## Detecting divergence: timestamps vs version vectors

Repair needs to know *which* version wins — and whether "newest" is even well-defined:

- **Last-write-wins (LWW):** compare wall-clock timestamps, highest wins. Simple, but **clock skew can silently drop a real update**, and two concurrent writes → one is lost with no signal.
- **Version vectors / vector clocks:** track per-replica counters so the coordinator can tell *descends-from* (safe to auto-pick the newer) from *concurrent* (a true **conflict** — siblings). For conflicts, read repair can't just pick one; it surfaces both for application/CRDT resolution or applies a defined merge. This ties directly to why quorum isn't linearizable ([[theory/consistency-models]]): concurrent writes produce siblings that *someone* must reconcile.

## What to actually memorize
1. **Quorum read finds disagreement ⇒ return newest to client + async write-back to stale replicas.**
2. It heals the **hot set for free** (piggybacks on reads); **cold keys need anti-entropy**.
3. **Foreground** (repair before responding, slower, stronger) vs **background** (off critical path, standard).
4. Needs a version rule: **LWW** (simple, lossy under skew/concurrency) vs **version vectors** (detects true conflicts → siblings).
5. One of the **three convergence legs** with hinted handoff + anti-entropy.

## Key points
- Cheapest convergence mechanism — no dedicated scan, rides on reads, prioritizes hot data automatically.
- Structurally incomplete on its own: unread keys never heal → anti-entropy is mandatory alongside it.
- Latency knob: blocking repair strengthens read monotonicity at a latency cost; async keeps reads fast.
- Conflict detection is only as good as the versioning: LWW hides concurrent-write loss; vector clocks expose it.
- A Dynamo-family primitive; assumes the store has a defined version-comparison / sibling-merge story.

## Interview angle

> "Read repair is the read-time half of convergence in a leaderless store. On a quorum read I compare the replicas' versions; if they disagree I return the newest to the client and asynchronously write it back to the stale ones, so any key that's being read heals itself essentially for free. That's the elegant part — repair prioritizes hot data by construction. But it only ever touches keys someone reads, so a written-but-never-read key stays stale forever — that's why it's always paired with Merkle-tree anti-entropy for the cold tail, and hinted handoff at write time. The subtlety is deciding the winner: last-write-wins on timestamps is simple but drops concurrent updates under clock skew, so if correctness matters I use version vectors to distinguish a newer version from a genuine concurrent conflict, and surface siblings for merge rather than silently picking one."

## Connections
- [[theory/vector-clocks]] — the version-comparison primitive read repair uses to tell stale (descendant, overwrite) from conflicting (sibling, keep both)
- [[system-design-concepts/hinted-handoff]] — write-time leg; the drift it creates is partly what read repair heals
- [[system-design-concepts/anti-entropy-merkle-trees]] — background leg; covers the cold keys read repair structurally can't
- [[theory/consistency-models]] — concurrent writes → siblings needing reconciliation is *why* quorum isn't linearizable; read repair is where you feel it
- [[system-design-concepts/leaderless-vs-leader-based]] — read repair is a leaderless-path necessity; a Raft leader's ordered log means replicas never silently diverge
- [[theory/durability-rpo-rto]] — version vectors vs LWW is a data-loss (lost-update) question in disguise

## Sources
- [[sources/docs/distributed-kv-store-mock-interview]] — §6 read repair (flagged), §8 leaderless conflict reconciliation, §9 quorum-not-linearizable
- [distributed-kv-store-mock-interview.md](https://github.com/redblackcoder/interview-prep-wiki/blob/master/sources/docs/distributed-kv-store-mock-interview.md) — full mock-interview design notes
- *Dynamo: Amazon's Highly Available Key-value Store* — DeCandia et al., SOSP 2007 (read repair, vector clocks)
