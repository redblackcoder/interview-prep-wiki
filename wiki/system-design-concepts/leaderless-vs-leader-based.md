# Leaderless vs Leader-Based Replication

The single most important fork in a distributed store's design: **who orders the writes?** Either *no one* does — any node coordinates, replicas reconcile after the fact (**leaderless**, Dynamo-style) — or *one elected leader per shard* does, imposing a total order via consensus (**leader-based**, Raft/Paxos). This choice cascades into consistency, availability, latency, and operational burden. A principal commits to one **write-path spine** and justifies it; you may layer a per-request read-consistency knob on top, but the write path is one architecture or the other.

## The one-sentence mental model

> **Leaderless trades consistency for availability — any node writes, conflicts are reconciled later; leader-based trades availability for consistency — one leader orders every write, and the shard stalls during elections. The 2007→2012 Dynamo→DynamoDB switch is the industry admitting the reconciliation burden usually isn't worth it.**

## The two architectures

```
 LEADERLESS (Dynamo)                    LEADER-BASED (Raft per shard)
 client → any coordinator               client → the shard's LEADER
   fan out to RF replicas                 leader appends to its log
   wait for W acks → done                replicate to followers
   reads: R replicas, newest wins         commit on majority → done
   conflicts? reconcile later             reads: leader (lease/ReadIndex)
                                          followers never coordinate writes
```

- **Leaderless:** the coordinator (any node — see [[theory/consistent-hashing]] for how it finds the replicas) writes directly to the RF replicas and counts **W** acks; reads gather **R** and pick the winner. No global order. Concurrent writes to one key both succeed → **siblings** to reconcile.
- **Leader-based:** each shard is a [[theory/consensus-raft|Raft]] group; the leader is the sole writer and imposes a real-time total order (see [[system-design-concepts/per-shard-raft]]).

## The decision table

| Dimension | Leaderless (Dynamo) | Leader-based (Raft per shard) |
|---|---|---|
| **Consistency ceiling** | eventual + staleness bound; **not** linearizable | **linearizable** per key |
| **Write path** | any coordinator, W acks | route to shard leader, majority commit |
| **Concurrent writes** | both succeed → siblings → **app/CRDT/LWW must reconcile** | leader serializes → no conflict |
| **Availability under partition** | **stays writable** (sloppy quorum + [[system-design-concepts/hinted-handoff]]) | minority side **can't write**; election gap on leader loss |
| **Write latency** | fast — local/nearest W replicas | 1 RTT to majority, every write |
| **Failure survival** | RF=3 + hinted handoff covers AZ+1 | RF=5 so 3-of-5 majority survives an AZ |
| **Operational burden** | **high** — version vectors, [[system-design-concepts/read-repair]], [[system-design-concepts/anti-entropy-merkle-trees]], conflict semantics exposed to clients | lower — one ordered log, no siblings |
| **Reconciliation owner** | the platform *and* leaks to app (conflict resolution) | fully internal to the group |

## The tell: why W=2/R=2 already picked your lane

The KV interview chose **quorum W=2/R=2** — that *is* the leaderless commitment. Quorum tuning, sloppy quorum, and hinted handoff are leaderless vocabulary; a Raft store doesn't expose W/R because the leader + majority-commit *is* the write rule. Running **both** — coordinator quorum *and* per-node Raft — means paying for coordination twice (the confusion the interview flagged). Pick the spine from what you're optimizing: **W/R quorum ⇒ leaderless; "linearizable" ⇒ leader-based.**

## The historical anchor: Dynamo (2007) → DynamoDB (2012)

Same company, same problem, opposite answer five years apart — and the *why* is the whole argument:

- **Dynamo (2007):** leaderless, vector clocks, client-side conflict resolution. Amazon's own teams found **reconciling siblings in application code miserable** — every app had to merge conflicting carts, handle LWW data loss, reason about staleness. The availability was great; the developer experience and correctness burden were not.
- **DynamoDB (2012):** leader-per-partition with Paxos replication, **strong consistency available as a first-class option**, conflicts handled *inside* the system. The industry lesson: **for a multi-tenant platform, exporting reconciliation to tenants is the wrong trade** — most teams want a store that just gives them the latest value.

That's the reasoning to reproduce: leaderless maximizes availability but **externalizes** the hard problem (conflict resolution); leader-based **internalizes** it at the cost of availability during elections. Modern default for a general-purpose platform leans **leader-based**, because [[theory/consistency-models|linearizability]] is what tenants expect and the operational burden lands on the platform, not 100 app teams.

## When leaderless still wins

Not obsolete — right when:
- **Availability/writes-always trumps consistency:** shopping carts, telemetry ingest, "never reject a write."
- **Conflicts are natural to merge:** **CRDTs** (counters, sets) reconcile deterministically, erasing the burden that sank Dynamo's UX.
- **Multi-master / multi-region active-active** where cross-region consensus latency is unacceptable — leaderless + causal/CRDT stays available (Cassandra, Riak, DynamoDB global tables).

## What to actually memorize
1. **The fork = who orders writes:** nobody (leaderless, reconcile later) vs one leader/shard (leader-based, ordered log).
2. **Leaderless = AP-ish:** available under partition (sloppy quorum + hinted handoff), not linearizable, **externalizes conflict resolution** (vector clocks / read-repair / anti-entropy / CRDT).
3. **Leader-based = CP-ish:** linearizable, internalizes conflicts, but **write-unavailable during elections** and 1-RTT-to-majority per write.
4. **W/R quorum ⇒ you're leaderless**; doing quorum *and* Raft = double coordination.
5. **Dynamo 2007 → DynamoDB 2012:** switched to leader-based because exporting reconciliation to app teams was the wrong platform trade. Leaderless survives for always-available / CRDT-mergeable / active-active cases.

## Key points
- Commit to **one write-path spine**; expose read-consistency as a per-request knob on top, not two write paths.
- The quorum knobs (W/R, sloppy quorum, hinted handoff) are leaderless-only vocabulary; their presence tells you the lane.
- Leaderless externalizes conflict resolution (its defining cost); leader-based internalizes it at an availability cost.
- The Dynamo→DynamoDB switch is the canonical evidence that platforms should absorb reconciliation, not export it.
- Leaderless remains correct for availability-first, CRDT-mergeable, or active-active multi-region workloads.

## Interview angle

> "The fork is: who orders writes? Leaderless — Dynamo-style — lets any coordinator write to W replicas and reconciles conflicts afterward; it stays available under partition via sloppy quorum and hinted handoff, but it's not linearizable and it pushes conflict resolution out to vector clocks, read-repair, anti-entropy, and ultimately the app. Leader-based — Raft per shard — routes every write through one leader that imposes a total order, so it's linearizable with no siblings, but the shard is write-unavailable during an election and every write costs a round trip to a majority. The tell for which lane you're in: if I'm tuning W and R, I've already chosen leaderless. My anchor is Dynamo 2007 versus DynamoDB 2012 — Amazon went from leaderless to leader-per-partition because making every app team reconcile siblings was the wrong trade for a platform. So I default a general-purpose store to leader-based per-shard Raft, and reach for leaderless only when availability must trump consistency or conflicts are CRDT-mergeable, like active-active multi-region."

## Connections
- [[theory/vector-clocks]] — the leaderless conflict-detection tax (siblings); leader-based needs none of it
- [[theory/consensus-raft]] — the ordering mechanism of the leader-based arm
- [[system-design-concepts/per-shard-raft]] — how leader-based scales while staying linearizable
- [[system-design-concepts/hinted-handoff]] — a defining leaderless mechanism; leader-based doesn't need it
- [[system-design-concepts/read-repair]] / [[system-design-concepts/anti-entropy-merkle-trees]] — the convergence machinery leaderless requires and leader-based avoids
- [[theory/consistency-models]] — the fork *is* the linearizable-vs-eventual choice; quorum≠linearizable is why leaderless can't reach the top
- [[theory/consistent-hashing]] — how a leaderless coordinator locates the replica set to write its quorum to
- [[system-design-concepts/rds-vs-key-value-store]] — same "who owns consistency" theme at the store-selection level

## Sources
- [[sources/docs/distributed-kv-store-mock-interview]] — §8 the coordinator fork (where the interview stopped), §4 quorum, §9 quorum-not-linearizable
- [distributed-kv-store-mock-interview.md](https://github.com/redblackcoder/interview-prep-wiki/blob/master/sources/docs/distributed-kv-store-mock-interview.md) — full mock-interview design notes
- *Dynamo: Amazon's Highly Available Key-value Store* (SOSP 2007) and the *Amazon DynamoDB* USENIX ATC 2022 paper — the leaderless→leader-based evolution
