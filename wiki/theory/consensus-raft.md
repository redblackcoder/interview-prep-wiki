# Consensus and Raft

**Consensus** is the problem of getting a group of nodes to agree on a single value (or a single ordered *log* of values) even though nodes crash and messages are lost or delayed. It is the machinery underneath every linearizable system: a replicated log built by consensus is *how* you get a single real-time order out of many machines. **Raft** is the consensus algorithm designed to be understandable, and the one you should be able to reason through at a whiteboard.

## The one-sentence mental model

> **Elect one leader per term; the leader is the sole writer and appends to a log it replicates to followers; an entry commits once a *majority* has it — and because any two majorities overlap, the next leader is guaranteed to already hold every committed entry.**

Everything in Raft is that **majority-overlap** argument applied twice: once to elect a leader, once to commit an entry.

## What problem it solves

Replicate a state machine (a KV store *is* a state machine: apply `put`/`delete` in order). If every replica applies the **same commands in the same order**, they stay identical. So consensus reduces to: **agree on an append-only, totally-ordered log of commands.** Do that and you have a linearizable replicated store (see [[theory/consistency-models]]).

Two classic algorithms: **Paxos** (Lamport — correct, famously hard to follow, underpins Chubby/Spanner) and **Raft** (Ongaro & Ousterhout, 2014 — same guarantees, decomposed for understandability). Raft is the one to know.

## Raft in three sub-problems

Raft deliberately splits into **leader election**, **log replication**, and **safety**.

### 1. Leader election

Time is divided into **terms** (monotonic integers). Each node is *follower*, *candidate*, or *leader*.

```
 follower ──(election timeout, no heartbeat)──▶ candidate
 candidate ──(wins majority of votes)─────────▶ leader
 candidate ──(hears higher term / new leader)─▶ follower
 leader    ──(discovers higher term)──────────▶ follower
```

A follower that hears no leader heartbeat before a **randomized** election timeout becomes a candidate, bumps the term, and requests votes. A node grants **one vote per term**. Win a majority → leader, and start sending heartbeats. The randomized timeouts make split votes rare and self-correcting.

```
 term 4:  ● A (leader) ──hb──▶ ● B      ● C
 A dies:                    ✕
 B times out first → candidate, term 5, "vote for me?"
          ● B ──RequestVote(term5)──▶ ● C   ● D …
 B gets majority (itself + C + D) → leader of term 5
```

**Why majority to elect:** it guarantees at most one leader per term (two leaders would need two majorities, which must overlap in ≥1 node, but that node only voted once). One leader per term = no split-brain writer.

### 2. Log replication

Client command → **leader** appends it to its log as `(term, index, command)` → leader sends `AppendEntries` to followers → once a **majority** (including itself) has stored the entry, the leader marks it **committed**, applies it to its state machine, and returns to the client. Committed entries are then applied by followers too.

```
 index:      1     2     3     4
 leader  A: [x=1][y=2][x=3][z=9]         ← appends z=9 at index 4
 follower B:[x=1][y=2][x=3][z=9]  ✔ has 4
 follower C:[x=1][y=2][x=3]              ← not yet
   z=9 is on A,B = majority(2 of 3) ⇒ COMMITTED ⇒ ack client, apply
```

`AppendEntries` doubles as the **heartbeat** and carries the leader's term + the previous entry's `(term,index)`, so a follower rejects anything that doesn't line up with its log — that consistency check is what repairs divergent followers (leader force-overwrites conflicting suffixes with its own).

### 3. Safety — the one invariant that makes it correct

The guarantee that ties it together: **a candidate cannot win election unless its log is at least as up-to-date as the voter's.** Combined with majority-commit:

```
 committed entry e is on a MAJORITY (that's what committed means).
 a winning leader has a MAJORITY of votes.
 two majorities overlap in ≥1 node  →  that node has e and would only
    vote for a candidate whose log includes e.
 ∴ every future leader already has every committed entry. It is never lost.
```

That is the crux move — **quorum intersection** — and it's worth being able to say in one breath, because it's the same argument behind quorum reads (`W+R>N`) in leaderless stores.

## Cost and what it buys

- **Latency:** a write costs one round trip from leader to a majority (1 RTT) before commit. Cross-AZ that's real milliseconds — the price of linearizability.
- **Availability:** progresses while a **majority is alive**. RF=5 → survives 2 failures (a full AZ of 2 + one more, or 3-of-5 across AZs); RF=3 → survives 1. During a leader election (a timeout, ~100s of ms) **that group can't commit writes** — the availability gap you must name.
- **Reads:** naive linearizable reads also go through the leader (and technically need a round trip to confirm still-leader). **Leader leases** or **ReadIndex** avoid the disk/rtt on every read; **follower reads** trade freshness for scale.

## What to actually memorize
1. **Consensus = agree on an ordered log**; apply the same commands in the same order → identical replicas → linearizable.
2. **Raft = leader election + log replication + safety**, all resting on **majority overlap**.
3. **One leader per term** (majority vote, one vote/term) → no split-brain; **commit = majority has the entry**.
4. **Safety invariant:** up-to-date-log restriction on voting ⇒ a new leader already holds every committed entry.
5. **Costs:** 1 RTT-to-majority per write; unavailable to writes during elections; reads need leases/ReadIndex to be both linearizable and cheap.

## Key points
- A replicated log built by consensus is the concrete mechanism behind linearizable/strict-serializable systems.
- **Majority (quorum) overlap** is the single idea reused for both election and commit — the same intersection principle as `W+R>N`, but here it yields a *total order*, which bare quorum does not.
- Raft needs a **majority alive** to make progress; even members (RF=4) buy no extra fault tolerance over RF=3 — always odd.
- The **election window** is a real write-availability gap; heartbeats + randomized timeouts keep it short and rare.
- Paxos/Multi-Paxos give the same guarantees; Raft is the teachable, widely-implemented (etcd, Consul, TiKV, CockroachDB) form.

## Interview angle

> "Consensus is agreeing on an ordered log of commands; apply them in the same order everywhere and the replicas are identical and linearizable. Raft makes that teachable in three parts. Election: terms with a randomized timeout, one vote per term, win a majority — so there's at most one leader per term. Replication: only the leader appends, and an entry commits once a majority has it. Safety: you can't win an election unless your log is at least as current as the voter's — and since any two majorities overlap, the new leader already holds every committed entry, so nothing committed is ever lost. The costs I'd name are a round-trip-to-majority per write and a short write-unavailability window during elections. It's the same quorum-intersection idea as W+R>N, except the leader's ordered log gives a real-time total order, which plain quorum can't."

## Connections
- [[theory/consistency-models]] — consensus is *how* you implement linearizable / strict serializable; quorum intersection here yields a total order, which bare `W+R>N` does not
- [[system-design-concepts/per-shard-raft]] — applying one Raft group per shard is how a partitioned store stays linearizable and scales writes
- [[system-design-concepts/leaderless-vs-leader-based]] — the write-path fork: Raft leader (this page) vs Dynamo-style coordinator quorum
- [[theory/durability-math]] — commit-to-majority is the *operational* durability (RPO≈0) whose statistical side that page quantifies
- [[system-design-concepts/work-distribution]] — leader election is the general "single owner per shard" pattern for coordinating a fleet

## Sources
- [[sources/docs/distributed-kv-store-mock-interview]] — §6 consensus (Raft) flagged, §8 leader-based path, §4 majority/quorum
- [distributed-kv-store-mock-interview.md](https://github.com/redblackcoder/interview-prep-wiki/blob/master/sources/docs/distributed-kv-store-mock-interview.md) — full mock-interview design notes
- *In Search of an Understandable Consensus Algorithm (Raft)* — Ongaro & Ousterhout, 2014
