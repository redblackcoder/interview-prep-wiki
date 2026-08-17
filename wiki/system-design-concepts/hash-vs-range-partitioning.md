# Hash vs Range Partitioning

Partitioning splits a keyspace across nodes so the store scales horizontally. There are two ways to decide *which* partition a key belongs to — **hash** the key, or place it by **range** — and the choice is a direct trade between **even load** and **efficient range scans**. You cannot have both from one partitioning function; the senior move is knowing which you gave up and how real systems buy part of it back.

## The one-sentence mental model

> **Hash partitioning scatters adjacent keys for uniform load but destroys range scans; range partitioning keeps adjacent keys together for cheap scans but invites hotspots — and DynamoDB's answer is to do *both* on a two-part key: hash the partition key, range the sort key within it.**

## The two schemes

```
 keys:  user#1  user#2  user#3  user#4  user#5  user#6
 ─────────────────────────────────────────────────────
 HASH   hash(k) mod P (or consistent-hash ring):
        user#1→P2  user#2→P0  user#3→P2  user#4→P1  user#5→P0  user#6→P1
        adjacent keys land on DIFFERENT partitions  → uniform, but scattered

 RANGE  contiguous key ranges per partition:
        [user#1..user#2]→P0   [user#3..user#4]→P1   [user#5..user#6]→P2
        adjacent keys land TOGETHER  → scannable, but skew-prone
```

## The core trade-off

| | Hash partitioning | Range partitioning |
|---|---|---|
| **Point lookup** (one key) | ✅ O(1) — hash to the owner | ✅ routing table / B-tree of ranges |
| **Range scan** (`user#1..#100`) | ❌ **scatter-gather over ALL partitions** — adjacent keys are everywhere | ✅ hits 1–few contiguous partitions |
| **Load distribution** | ✅ uniform by construction (hash spreads) | ❌ hot ranges (recent timestamps, `a…` names) overload one partition |
| **Sequential-write hotspot** | ✅ spread across partitions | ❌ monotonic keys (timestamps, autoinc IDs) all hit the *last* partition |
| **Rebalancing** | consistent hashing + vnodes (see [[theory/consistent-hashing]]) | split/merge ranges dynamically (Bigtable tablets, CockroachDB) |

The tension is irreducible: a **range scan wants adjacent keys co-located**, while **even load wants adjacent keys scattered**. One function can't do both.

### Why the hash range-scan is so bad

"Give me `user#1000 … user#2000`" under `hash(key)`: those 1000 consecutive keys hash to 1000 ~uniformly-random partitions. So the query fans out to **every partition in the cluster**, each does a tiny slice, and the coordinator merge-sorts the lot — *a full-cluster scatter-gather to answer what looked like a small range*. That's not a range query, it's a full scan wearing a trenchcoat.

## DynamoDB's resolution: a composite key

The trick is to stop asking one function to do everything. DynamoDB (and Cassandra) split the key:

```
 primary key = (partition_key, sort_key)
                    │             │
       hash THIS ───┘             └─── RANGE within the partition (sorted on disk)
```

- **Partition key** is *hashed* → picks the node. Uniform load, no global hotspot.
- **Sort key** stores rows **sorted contiguously *inside* that partition** → an efficient range scan **as long as it's scoped to one partition key.**

```
 partition key = user#42   (hashed to P1)
   inside P1, sorted by sort_key:
     (user#42, 2026-01-01)  ┐
     (user#42, 2026-01-02)  ├─ "user#42's orders in Jan" = one contiguous
     (user#42, 2026-01-03)  ┘   in-partition scan. Fast.
```

So `Query(partition_key = user#42, sort_key BETWEEN jan..feb)` is efficient; it touches one partition and walks a sorted run. This is the model the KV interview settled on: **range queries are a first-class operation only *within* a partition key.**

### What it makes cheap, and what it quietly forbids

- ✅ **Cheap:** point lookup; range/`ORDER BY`/`BETWEEN` over the sort key for a *single* partition key.
- ❌ **Forbidden (efficiently):** a range scan **across** partition keys ("all users A–M", "all orders globally in January"). That's back to a full scatter-gather — hash destroyed the cross-partition ordering on purpose.

**Escape hatch — the Global Secondary Index (GSI):** a *second copy* of the data partitioned/sorted by a **different** key, so a query pattern the base table can't serve cheaply becomes a single-partition query on the index. The cost is real and worth stating: extra storage (a full projection), extra write amplification (every base write updates the index), and **asynchronous index maintenance → the GSI is eventually consistent** with the base table. You buy the access path with storage, write cost, and a consistency relaxation.

## The hot-partition failure mode (name it)

Even hashed, a *single* partition key can be scorching: one celebrity `user_id`, one viral tweet's `post_id`. Hashing distributes *across* keys, not *within* one — so all of one hot key's traffic still lands on one partition. Mitigations: **write sharding** (append a suffix `hotkey#0..#N`, scatter, gather on read), caching the hot item, or an adaptive split. This is the range-partition hotspot re-appearing at the single-key level, and interviewers probe it.

## What to actually memorize
1. **Hash = uniform load, no range scans; Range = cheap scans, hotspot-prone.** One function can't give both.
2. Under hash, a cross-key range scan is a **full-cluster scatter-gather** — the disqualifying cost.
3. **DynamoDB composite key:** hash the **partition key**, sort the **sort key within** it → range scans are cheap *only within one partition key*.
4. **Cross-partition ranges need a GSI** = a second differently-partitioned copy → extra storage + write amp + eventual consistency.
5. **Hot partition:** hashing spreads across keys, not within one; a single hot key still needs write-sharding/caching.

## Key points
- The choice is a load-vs-scan trade; picking hash means consciously giving up global range scans.
- Composite `(partition_key, sort_key)` is how you recover *in-partition* range queries without reintroducing global hotspots.
- Monotonic keys (timestamps, autoinc) are the classic range-partition hotspot — hashing or key-salting fixes it.
- Secondary access paths (GSIs) are extra partitioned copies with their own storage, write, and consistency cost — not free indexes.
- A single hot partition key is a distinct failure mode from partition skew and needs its own mitigation.

## Interview angle

> "I pick the partition function off the access pattern. Hashing gives uniform load but scatters adjacent keys, so any cross-key range scan becomes a full-cluster scatter-gather — disqualifying if range queries matter. Range partitioning keeps neighbors together for cheap scans but creates hotspots, especially on monotonic keys like timestamps. DynamoDB's move is to do both on a composite key: hash the partition key so load spreads, and store the sort key sorted *within* the partition so range queries are cheap — but only when scoped to one partition key. A cross-partition range needs a global secondary index, which is a second copy partitioned differently, so I'm paying storage, write amplification, and eventual consistency for that access path. And I'd call out the hot-partition case — hashing spreads across keys, not within one, so a celebrity key still needs write-sharding or caching."

## Connections
- [[theory/consistent-hashing]] — the mechanism behind the *hash* side; vnodes are how hashed partitions balance and rebalance
- [[system-design-concepts/rds-vs-key-value-store]] — a relational store's arbitrary `WHERE`/`JOIN` is exactly what partitioning gives up for horizontal write scale
- [[system-design-concepts/leaderless-vs-leader-based]] — the partition is the unit each replica set / Raft group owns
- [[theory/consistency-models]] — the GSI's "eventually consistent with the base table" is this spectrum applied to a secondary index
- [[system-design-concepts/work-distribution]] — partitioning work across a fleet is the same scatter/hotspot problem in a different guise

## Sources
- [[sources/docs/distributed-kv-store-mock-interview]] — §7 hash-vs-range + composite key, §2 sort-key range-query requirement, §8 GSI escape hatch
- [distributed-kv-store-mock-interview.md](https://github.com/redblackcoder/interview-prep-wiki/blob/master/sources/docs/distributed-kv-store-mock-interview.md) — full mock-interview design notes
