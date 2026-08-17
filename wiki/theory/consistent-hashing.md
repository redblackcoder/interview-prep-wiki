# Consistent Hashing

The partition-assignment primitive for a dynamic fleet: map keys to nodes so that **adding or removing a node moves only ~1/N of the keys**, not all of them. Everything else (load balance, rebuild speed, even durability) follows from *how* you place nodes on the ring — which is why virtual nodes and copysets, below, are the parts that actually matter at a whiteboard.

## The one-sentence mental model

> **Hash keys and nodes onto the same ring and walk clockwise to find the owner; a node change then disturbs only its ring-neighborhood — but *how many times* you place each node on the ring is a knob that trades load-balance and rebuild-speed against durability.**

## Why not `hash(key) % N`

The naive scheme dies on *rebalance*, which is the common case, not the exception:

```
key k lands on node (hash(k) mod N)
 N=4:  hash(k)=17 → 17 mod 4 = 1        ── node 1
 add one node, N=5:  17 mod 5 = 2       ── node 2   ← moved!
```

Changing N remaps **almost every key** (the modulus changed for all of them) → a near-total reshuffle: every cache miss at once, or every row copied to a new owner. `% N` is only safe if N never changes, which for a real fleet it always does.

## The ring

Hash both keys and nodes into the same space (`0 … 2^m−1`), wrap it into a circle. **A key is owned by the first node clockwise.** For replication, it's owned by the next **RF distinct** nodes clockwise.

```
        N_A
      ┌──●──┐          key k hashes here ─┐
  N_D ●     ● N_B                         ▼
      └──●──┘          …──● N_A ─────k────● N_B──…
        N_C                                ▲ first node clockwise = owner
                                           RF=3 → also N_C, N_D (next 2 distinct)
```

Add a node: it drops between two existing nodes and takes **only the arc between its predecessor and itself** — one neighbor's slice moves, nobody else's. Remove a node: its arc passes to the **single** next node clockwise. That locality — O(K/N) keys move, not O(K) — is the whole point.

## The problem the plain ring still has

Nodes land at *random* positions, and two failures follow from that single fact:

**1. Load variance.** Random points don't cut the ring into equal arcs. A single random arc has ~100% variability (coefficient of variation ≈ 1) → one unlucky node owns several× its fair share, a permanent hotspot baked into where the hash fell.

**2. Lumpy failure/rebalance.** A node's whole arc dumps on *one* neighbor, and rebuild streams from essentially *one* source:

```
 …──● N_A ───────────● N_B ────────● N_C──…
 N_B dies:
 …──● N_A ─────────────────────────● N_C──…
          ▲ N_C inherits N_B's ENTIRE range — load ~doubles, one-source rebuild = slow
```

## Virtual nodes (the fix)

Hash each **physical** node onto the ring **V times** — `hash("B#1") … hash("B#256")` — so it owns V small scattered arcs instead of one big one. Each of those V positions is a **virtual node (vnode)**, called a **token** in Cassandra. Origin: the **Amazon Dynamo paper (2007)**.

```
 V=4 — B's four vnodes (b) scattered; B owns 4 slivers, not 1 arc:
        a  b
      d       c        a node's load = SUM of V random slivers
     c    ⊙    a       → averaging shrinks variance ~1/√V
      b       d           V=256 → ~100% variance down to ~6%
        a  c  b
```

Both problems dissolve:
- **Balance:** load is now the sum of V independent arcs → variance ~`1/√V`.
- **Smooth, parallel recovery:** a dead node's V slivers each fall to a *different* successor, so its load spreads over ~V nodes and **rebuild streams from ~V sources in parallel** → low MTTR. This is exactly the fast-re-replication that [[theory/durability-math]] depends on.

## Vnodes and durability: the copyset trade-off

Vnodes are not free — they change durability, and in *both* directions. A **copyset** is a group of RF nodes that together hold a full copy of some partition; data is lost in an RF-node concurrent failure **iff those nodes form a copyset**, so `#copysets` is the fleet-wide loss multiplier (see [[theory/durability-math]] Step 6):

```
 no vnodes:  a node shares data only with fixed neighbors → #copysets ≈ N
 V vnodes:   a node's tokens scatter → shares with ~RF·V nodes → #copysets ≈ N·V
             (cap: C(N,RF))
```

Two opposing channels into `P(any loss) ∝ #copysets · MTTR²`:

| Vnodes | #copysets (∝ P loss) | MTTR (squared in P loss) |
|---|---|---|
| **More (V↑)** | ↑ ~linearly → **hurts** | ↓ ~1/V (parallel rebuild) → helps |
| **Fewer (V↓)** | ↓ → helps | ↑ lumpier, slower → hurts |

Idealized, the squared-MTTR win would dominate (`V·(1/V)² = 1/V`), but **rebuild speedup saturates** (you cap rebuild bandwidth to protect live traffic) while `#copysets` keeps climbing linearly — so past a modest V the copyset term wins and vnodes **net-hurt loss frequency**. This is why **Cassandra changed its `num_tokens` default from 256 → 16**. Crucially: this only moves the *frequency/shape* of loss (rare-catastrophic ↔ frequent-trivial); **per-object durability is one copyset, ~11 nines, independent of V.** (Reference: Copysets, Cidon et al., USENIX ATC 2013.)

## What to actually memorize
1. **Ring + walk clockwise**; RF = next RF distinct nodes. Node change moves O(K/N), not O(K) — vs `%N` which moves ~everything.
2. **Vnodes = each machine placed V times** → load variance ~`1/√V`, and rebuild parallelizes across ~V sources.
3. **The copyset tension:** more vnodes → faster rebuild (helps) *but* `#copysets ≈ N·V` → more loss events (hurts) → real systems pick modest V (Cassandra 16).
4. **Per-object durability is V-independent**; vnodes only reshape fleet-wide loss frequency.

## Key points
- `hash(key) % N` reshuffles ~all keys on any node change; the ring localizes movement to O(K/N).
- Plain ring has random-arc load variance and one-neighbor/one-source failure handling — **vnodes fix both** and are essential in practice.
- Vnodes carry a **durability cost via copysets** (`#copysets ≈ N·V`) that partly offsets their rebuild-speed benefit → tune V, don't maximize it.
- The knob reshapes loss *frequency vs magnitude*; the per-object SLA number doesn't move.

## Interview angle

> "Consistent hashing puts keys and nodes on a ring and walks clockwise, so a node change moves only ~1/N of keys instead of everything under `mod N`. But a plain ring has bad load variance and dumps a dead node's whole range on one neighbor, so in practice I place each node V times as virtual nodes — load becomes a sum of V slivers (variance ~1/√V) and rebuild streams from ~V sources in parallel, which is what keeps MTTR low. The subtlety is that vnodes aren't free for durability: a node now shares data with ~RF·V others, so the number of copysets grows ~N·V and *any-loss* events get more frequent even as each shrinks. That's why Cassandra dropped its token default from 256 to 16. Per-object durability doesn't change — vnodes just trade rare-catastrophic loss for frequent-trivial."

## Connections
- [[theory/durability-math]] — vnodes set both terms of fleet loss: `#copysets ≈ N·V` and the MTTR that parallel rebuild lowers
- [[system-design-concepts/hash-vs-range-partitioning]] — consistent hashing is the *hash* side; this is why range scans scatter across the ring
- [[system-design-concepts/work-distribution]] — consistent hashing is the default partition-assignment strategy for a worker fleet
- [[system-design-concepts/web-crawler]] — host-to-shard assignment with minimal content movement on rebalance
- [[system-design-concepts/message-fanout]] — the routing ring FastGlobal serves copy-free; `phash2` spreads PIDs across cores
- [[system-design-concepts/leaderless-vs-leader-based]] — the ring assigns the replica set that a leaderless coordinator writes its quorum to

## Sources
- [[sources/docs/web-crawler-system-design]] — consistent hashing for host-to-shard assignment with minimal content movement on rebalance
- [[sources/docs/distributed-kv-store-mock-interview]] — §7 partitioning (ring, vnodes, control-plane-managed), §5 durability (copyset link)
- [distributed-kv-store-mock-interview.md](https://github.com/redblackcoder/interview-prep-wiki/blob/master/sources/docs/distributed-kv-store-mock-interview.md) — full mock-interview design notes
