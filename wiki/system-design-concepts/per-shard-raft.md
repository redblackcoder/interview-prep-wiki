# Per-Shard Raft

How you get **linearizable consistency *and* horizontal scale** out of a consensus algorithm that, by itself, doesn't scale. A single Raft group has a fixed write ceiling (one leader, every write through it). The move: **partition the keyspace into many shards and run an independent Raft group per shard.** Each shard is strongly consistent on its own; the cluster scales by adding shards. This is the architecture behind Spanner, CockroachDB, TiKV, and the leader-based (post-2012) DynamoDB path.

## The one-sentence mental model

> **One Raft group can't scale — one leader bottlenecks writes — so shard the keyspace and give each shard its own Raft group with its own leader; the cluster's write throughput becomes the sum of all shard leaders, while each shard stays linearizable.**

## Why not one big Raft group

Consensus ([[theory/consensus-raft]]) gives linearizability, but a single group has hard ceilings: **all writes serialize through one leader**, the log is one ordered stream, and adding members makes writes *slower* (bigger quorum to wait for), not faster. So one group maxes out at one machine's write rate. Sharding breaks that:

```
 keyspace ─partition→  shard A     shard B     shard C
                      Raft grp A   Raft grp B   Raft grp C
                      leader L_A   leader L_B   leader L_C
                      on node 3    on node 7    on node 1
   write to a key → route to its shard's leader → that group commits independently
   cluster write throughput ≈ Σ shard leaders     (scales with shard count)
```

Leaders are **spread across nodes** (a node is leader for some shards, follower for others), so write load balances instead of piling on one machine.

## Anatomy

- **Shard = one Raft group.** Its RF nodes are the group members; its leader serves that shard's reads/writes; the partition map (which key → which shard) lives in a control plane, same as the ring in [[theory/consistent-hashing]].
- **RF=5 is the common choice** (vs 3 for leaderless). Raft needs a **majority alive**, and RF=5 tolerates **2 failures** — the concrete win being **survive a full AZ (2 members) and still form a 3-of-5 majority** across the other AZs. RF=3 tolerates only 1, so an AZ loss + any straggler stalls the group.

```
 RF=5 across 3 AZs:  AZ1[m1 m2]  AZ2[m3 m4]  AZ3[m5]
   lose AZ1 (m1,m2):  m3,m4,m5 = 3 of 5 = majority ⇒ elect leader, keep committing ✔
   (RF=3, one per AZ: lose an AZ ⇒ 2 of 3 still works, but AZ+straggler ⇒ 1 of 3 ⇒ stall)
```

## Reads: linearizable without a round trip per read

Naive linearizable reads go through the leader *and* need a round trip to confirm it's still leader (a deposed leader could serve stale data). Two standard optimizations:

- **Leader leases:** the leader holds a time-bounded lease; within it, it answers reads locally, no quorum round trip. Needs bounded clock drift.
- **ReadIndex:** leader confirms its commit index with a lightweight majority check once, then serves a batch of reads.
- **Follower reads:** followers serve reads at a known-committed index — scales read throughput, trading a bounded staleness (no longer strictly linearizable unless lease-guarded).

Read scaling matters because the interview's envelope was **read-heavy (≈10:1)** — you don't want every read to consume leader write-capacity.

## Costs (state them)

- **Write latency = 1 RTT to a majority**, every write. Cross-AZ that's real milliseconds — the linearizability tax vs a leaderless local-quorum ack.
- **Election window:** if a leader dies, that shard is **write-unavailable for the election timeout** (~100s of ms). Sharding *contains* this — only the dead leader's shards stall, not the cluster — but it's a real per-shard availability gap against a tight write SLO.
- **Cross-shard operations lose the easy guarantee.** One key = one shard = linearizable for free. A transaction spanning shards needs **2-phase commit across Raft groups** (the Spanner/CockroachDB model), which is far more expensive — the reason the KV data model discourages cross-partition transactions in the first place.
- **Hot shard:** one shard's leader can still saturate; needs shard splitting (range) or better key hashing.

## What to actually memorize
1. **One Raft group doesn't scale** (single-leader write ceiling) → **shard the keyspace, one Raft group per shard**, leaders spread across nodes.
2. Per-shard = **linearizable within a shard**; cluster throughput = **Σ shard leaders**.
3. **RF=5** so a full-AZ loss still leaves a 3-of-5 majority (RF=3 can't survive AZ+1 for consensus).
4. **Reads** use leader leases / ReadIndex (cheap linearizable) or follower reads (scale, bounded staleness).
5. **Cross-shard transactions need 2PC across groups** — expensive; single-key is the free case.

## Key points
- The standard way to reconcile "consensus is strongly consistent" with "consensus doesn't scale": many groups, one per partition.
- RF=5 is a consensus **availability** choice (survive AZ+1 on a majority), not a durability one — contrast the leaderless RF=3 + hinted handoff path.
- Reads need explicit design (leases/ReadIndex/follower reads) or they burn leader capacity — critical for read-heavy workloads.
- Per-shard elections localize write-unavailability to one shard, but the gap is real against a strict write SLO.
- Multi-shard atomicity requires 2PC and sacrifices the cheap single-shard linearizability — architecturally discouraged.

## Interview angle

> "A single Raft group is linearizable but caps at one leader's write rate and gets slower as you add members, so it doesn't scale. The fix is per-shard Raft: partition the keyspace and run an independent Raft group per shard, with leaders spread across nodes, so cluster write throughput is the sum of all shard leaders while each shard stays linearizable. I'd run RF=5 rather than 3, because consensus needs a live majority and RF=5 keeps a 3-of-5 majority through a full-AZ loss. Reads I'd serve with leader leases or ReadIndex so linearizable reads don't pay a round trip each, and follower reads where I can tolerate bounded staleness — important because this workload is read-heavy. The costs I'd name: a round-trip-to-majority per write, a short per-shard write outage during elections, and that anything spanning shards needs 2-phase commit across groups — which is exactly why the KV model steers you to single-partition operations."

## Connections
- [[theory/consensus-raft]] — the single-group algorithm this page scales out; majority-commit and elections are per shard here
- [[system-design-concepts/leaderless-vs-leader-based]] — this is the leader-based arm of the fork; the post-2012 DynamoDB choice
- [[theory/consistency-models]] — per-shard Raft is how you actually deliver linearizable single-key ops that quorum can't
- [[theory/consistent-hashing]] — the partition map assigning keys to shards is the same control-plane ring
- [[theory/durability-math]] — RF=5-for-majority (availability) vs RF=3-for-durability are different reasons to pick RF
- [[system-design-concepts/hash-vs-range-partitioning]] — how the shard boundaries are drawn; cross-shard = cross-partition = the expensive case

## Sources
- [[sources/docs/distributed-kv-store-mock-interview]] — §6 per-shard Raft RF=5 (flagged), §4 AZ+1 majority survival, §2 read-heavy envelope
- [distributed-kv-store-mock-interview.md](https://github.com/redblackcoder/interview-prep-wiki/blob/master/sources/docs/distributed-kv-store-mock-interview.md) — full mock-interview design notes
