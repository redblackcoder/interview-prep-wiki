# Consistency Models: A Reasoning Framework

A **consistency model** is the contract for what a read may return when data is replicated and operations run concurrently. There are ~16 named models in the full lattice ([Jepsen](https://jepsen.io/consistency/models) is the canonical reference), but you don't reason at an interview by reciting them. You reason from a **small frame plus three derivation tools**, and read the specific rung off that. This page is that frame — enough to *derive* the answer, not memorize a diagram.

## The one-sentence mental model

> **Two independent axes — how operations on *one object* order, and how *multi-object transactions* isolate — and one rule that tells you, for any rung, whether it can survive a network partition.**

## The frame: two axes, one fused top

Almost every model is a point on one of two ladders:

- **Single-object / ordering axis** — given concurrent reads and writes to *one* key, in what order do they appear? (eventual → causal → sequential → linearizable)
- **Multi-object / transactional axis** — given transactions touching *many* keys, what interleavings are forbidden? (read uncommitted → read committed → snapshot isolation → serializable)

The two ladders are **independent** — a store can be linearizable per key but only read-committed across a transaction, or serializable but not real-time. They fuse only at the very top: **strict serializable = serializable (multi-object) + linearizable (real-time)** — the whole database behaves like one machine executing one transaction at a time. That fusion is why it's the strongest and most expensive model.

So the first move on any consistency question: **"single-key ordering, or multi-key transaction?"** That picks the ladder before you argue rungs.

## Ladder 1 — single-object ordering (weakest → strongest)

| Model | Guarantee | Anomaly it kills | Partition? |
|---|---|---|---|
| **Eventual** | If writes stop, replicas converge. A read may return any past value. | — | Totally available |
| **Session guarantees** | Per-client: **read-your-writes**, **monotonic reads** (never go backward in time), **monotonic writes** (your writes apply in order) | client sees own history incoherently | Sticky available |
| **PRAM** | = the three session guarantees together: every client sees each writer's writes in that writer's program order (per-writer FIFO) | reordering one writer's stream | Sticky available |
| **Causal** | PRAM **+ writes-follow-reads**: cause→effect ops appear in that order to *everyone*; only *concurrent* ops may be seen in different orders | causality violation (see the effect before the cause) | **Sticky available** |
| **Sequential** | *A* single total order exists, respecting each process's program order — but **not** real time | different clients disagree on a global order | Unavailable |
| **Linearizable** | Sequential **+ real time**: once a write acks, every later read sees it (or newer). Behaves like one copy. | stale read after an acknowledged write | Unavailable |

## Ladder 2 — multi-object transactions (weakest → strongest)

The transactional ladder is defined by **which anomalies it forbids** — that's the whole reasoning device. Learn the five anomalies (next section) and each level is just "the line above which these are banned."

| Level | Prevents | Still allows | Partition? |
|---|---|---|---|
| **Read uncommitted** | dirty write | dirty read, fuzzy read, phantom, lost update, write skew | Totally available |
| **Read committed** | + dirty read | fuzzy read, phantom, lost update, write skew | Totally available |
| **Snapshot isolation (SI)** | + fuzzy read, + **lost update** (first-committer-wins on write-write) | **write skew**, some phantoms | Unavailable |
| **Serializable** | + phantom, + write skew — *some* serial order | (nothing; but no real-time order) | Unavailable |
| **Strict serializable** | + real-time order | — | Unavailable |

Note SI is **not** simply "below serializable on one line" — it forbids lost update but *permits write skew*, which is exactly the gap that makes it cheaper and the classic interview trap ("we use SI, are we safe against X?" → depends whether X is a write skew).

## Tool 1 — the anomaly cheat-sheet (the *why* behind every rung)

Each rung exists to forbid one concrete bad behavior. Notation: `w1(x)` = txn 1 writes x; `r2(x)` = txn 2 reads x.

| Anomaly | Shape | Plain English |
|---|---|---|
| **Dirty write** | `w1(x) … w2(x)` (before commit) | overwrite another txn's *uncommitted* write |
| **Dirty read** | `w1(x) … r2(x)` | read a value that was never committed |
| **Fuzzy / non-repeatable read** | `r1(x) … w2(x) … r1(x)` | re-read the same row, get a different value |
| **Phantom** | `r1(predicate) … w2(row matching predicate)` | re-run the same query, the *set* of matching rows changed |
| **Lost update** | `r1(x) … r2(x) … w1(x) … w2(x)` | two read-modify-writes, one silently clobbers the other |
| **Write skew** | `r1(x,y) r2(x,y) … w1(x) w2(y)` | each txn reads a shared set, writes a *disjoint* part, together they break an invariant (e.g. both on-call doctors go off-shift) |

If you can name the anomaly, you can place the requirement: "this needs write-skew prevention → SI isn't enough → serializable." That derivation *is* the senior signal — far more than reciting that repeatable-read sits above read-committed.

## Tool 2 — will it survive a partition? (derive the tier, don't memorize it)

Jepsen tags every model **totally available**, **sticky available**, or **unavailable**. You don't memorize the table — you derive it from one rule:

> **A model is available under a partition iff a node can satisfy it using only state it already holds — its own committed data, its client's session, and causally-visible writes. The moment a guarantee needs a *global decision* — a single total order, or detecting a conflicting write *anywhere* — it cannot be met on one side of a partition, so it's unavailable.**

Applying the rule:

- **Totally available** (any client, any node): eventual, read-uncommitted, read-committed, writes-follow-reads. Local committed state suffices.
- **Sticky available** (available *if* each client stays pinned to one node): read-your-writes, monotonic reads/writes, PRAM, **causal**. You need the history *your session* has seen — a fresh node in the partition doesn't have your writes, so you must "stick."
- **Unavailable** (needs synchronization): sequential, linearizable, SI, serializable, strict serializable. All require a global order or global conflict check.

Two payoffs of the rule:
1. **Causal is the strongest model that stays available under partition** (sticky). That's the CAP ceiling for an always-on store — the precise version of "CAP says pick AP or CP."
2. It tells you *why* SI is unavailable despite feeling lightweight: first-committer-wins must detect a conflicting write *anywhere* → global check → partition-intolerant.

## Tool 3 — the interview trap: quorum (W+R>N) is **not** linearizable

The most common wrong claim: "Dynamo-style W+R>N gives strong consistency." What W+R>N actually guarantees is **intersection** — the read quorum and the latest write quorum share ≥1 node, so a read *touches* a replica holding the newest acked value. That is weaker than linearizable, concretely:

- **No total order.** Quorum arbitrates one key's copies, not a global operation order.
- **Concurrent writes make siblings.** Two coordinators each hit W replicas; both "succeed"; a later read sees *two* values with no winner → you need **version vectors** (or last-write-wins, which silently drops an update). Needing reconciliation is the tell you're below linearizable.
- **Read-repair / sloppy-quorum (hinted-handoff) races** can still return an older value transiently.

So leaderless quorum ≈ **eventual + a staleness bound**, not linearizable. To get linearizability you route the key through a **single order-imposing authority** — a per-shard leader running consensus (Raft/Paxos). That is exactly the leaderless-vs-leader-based fork: quorum buys availability + tunable staleness; per-shard Raft buys linearizability at the cost of a leader round-trip and unavailability during elections. And the platform corollary: whatever you relax below linearizable, the **store** reconciles (read-repair + anti-entropy, or consensus) — you never export version reconciliation to tenant apps.

## What to actually memorize

The rest you derive. Hold onto:
1. **Two axes** (single-object ordering / multi-object transactions), fusing at strict-serializable.
2. **Single-object rungs:** eventual → causal → sequential → linearizable. (Causal bundles the session guarantees.)
3. **The 6 anomalies** — they define the transactional ladder *and* let you reason "what does this requirement need."
4. **The partition rule:** local/causal state = available; global order or global conflict check = unavailable → **causal is the strongest available-under-partition model.**
5. **W+R>N ≠ linearizable** → to get linearizable, add a per-shard leader.

## Key points
- Consistency is two independent ladders, not one line; fused only at strict serializable (= serializable + linearizable).
- The transactional ladder *is* an anomaly ladder — name the anomaly (esp. **write skew**, which SI permits) and the required level falls out.
- **Derive availability from one rule**: needs a global decision ⇒ unavailable; needs only local/session/causal state ⇒ available (sticky if it needs *your* history).
- **Causal = strongest model available under partition** — the operational meaning of "AP."
- **W+R>N gives quorum intersection, not linearizability**; linearizable single-key needs consensus per shard.

## Interview angle

> "I don't recite the lattice — I start with two questions. One: single-key ordering or a multi-key transaction? That picks the ladder. Two: does the requirement forbid a specific anomaly? For transactions I reason in anomalies — dirty read, non-repeatable read, phantom, lost update, write skew — and the level is just the line that bans the one I care about; the classic trap is snapshot isolation, which stops lost update but *allows write skew*. For availability I use one rule: if a guarantee needs a global decision — a total order or detecting a conflict anywhere — it can't survive a partition, so linearizable and serializable are unavailable, while causal is the strongest model that stays available. And the quorum trap: W+R>N only means the read and write sets intersect, so a read sees the latest acked write — but there's no total order and concurrent writes still need version-vector reconciliation, so it's not linearizable. For linearizable I put a per-shard leader in front."

## Connections
- [[theory/vector-clocks]] — how concurrent writes are *detected* as siblings; the concrete reason quorum `W+R>N` isn't linearizable
- [[theory/durability-math]] — the sibling non-functional axis; the W in W+R>N is also what makes an acked write operationally durable
- [[system-design-concepts/rds-vs-key-value-store]] — ACID/serializable (relational) vs per-command/eventual (KV) is this frame applied to the store choice
- [[theory/durability-rpo-rto]] — RPO is "how much can I lose"; consistency is "what order can I observe" — orthogonal contracts
- [[theory/consistent-hashing]] — quorum is defined over the replica set the ring assigns to each key
- [[system-design-concepts/global-rate-limiting]] — "consistent vs accurate" counters is this exact trade-off (CRDT/eventual, sticky-available) in a concrete subsystem
- [[theory/copy-on-write-vs-mvcc]] — MVCC is *how* SI reads a consistent snapshot without blocking writers (the mechanism behind Ladder 2's middle)

## Sources
- [[sources/docs/distributed-kv-store-mock-interview]] — §9 consistency spectrum, §4 quorum, §8 leaderless-vs-leader-based fork
- [distributed-kv-store-mock-interview.md](https://github.com/redblackcoder/interview-prep-wiki/blob/master/sources/docs/distributed-kv-store-mock-interview.md) — full mock-interview design notes
- [Jepsen: Consistency Models](https://jepsen.io/consistency/models) — the canonical formal lattice this page distills; Bailis et al., *Highly Available Transactions* is the source of the availability classification
