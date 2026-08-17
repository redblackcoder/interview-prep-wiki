# Hinted Handoff

The mechanism that keeps a leaderless, quorum-replicated store **writable through node and AZ failures without raising the replication factor.** When a replica that *should* hold a write is down, the coordinator stashes the write on a healthy stand-in, tagged with a **hint** saying who it really belongs to, and delivers it when the owner returns. It is the reason RF=3 can survive "AZ + 1" without becoming RF=9.

## The one-sentence mental model

> **If a home replica is down, don't fail the write — park it on the next healthy node with a note ("this belongs to N3"), count it toward the write quorum, and hand it back when N3 recovers.**

## The problem it solves

Strict quorum says a write needs **W** of its **RF** home replicas to ack. If enough home replicas are down, strict quorum **blocks the write** — you've traded availability for consistency. For an always-on platform that's often the wrong trade. But the naive fix ("just lower W") violates `W+R>N` and silently loses the read/write overlap. Hinted handoff is the disciplined middle: **keep W durable copies, just not all on the home nodes.**

## How it works

```
 key k's home replicas (RF=3):  N1  N2  N3      W=2 required
 N3 is DOWN.

 write k ─▶ coordinator
             ├─▶ N1  ✔ (home)
             ├─▶ N2  ✔ (home)
             └─▶ N3  ✕ down
                  └─▶ N4 ✔  stores k + HINT{owner:N3}   ← the handoff
             quorum: 3 durable copies (N1,N2,N4) ≥ W ⇒ ACK client
```

`N4` is not a home replica for `k`; it holds the data in a separate **hints** area, keyed by "for N3." When `N4` sees `N3` alive again:

```
 N3 recovers ─▶ N4 replays its hints ─▶ N3 now has k ─▶ N4 deletes the hint
```

This is **sloppy quorum**: W acks, but the acking set may include non-home nodes. (Strict quorum = all acks from home replicas.) The system stays at its durability target throughout — there were always W copies on disk.

## Why it beats "raise RF" for AZ+1

The KV interview's failure model was **survive a full AZ loss plus one more node.** The tempting fix was RF=6 (2 replicas/AZ). But strict-quorum AZ+1 across 3 AZs actually needs RF=9 — absurd (see [[theory/durability-math]] on why RF isn't the lever). Hinted handoff covers the gap at **RF=3**:

```
 RF=3, one replica per AZ.  AZ-C fails (N3 gone) + straggler N1 flaps.
 strict quorum W=2:  only N2 home-available ⇒ WOULD BLOCK
 sloppy quorum:      N2 ✔ + N4/N5 take hinted copies ⇒ still W durable ⇒ WRITABLE
```

You buy write-availability through the outage window without paying 2–3× storage every day for a rare failure.

## The cost: it creates drift (and needs friends)

The hinted copy is in the *wrong place*, so the moment it exists your replicas are **inconsistent**, and a strict read of the home set can miss it. Hinted handoff is therefore never deployed alone — it's one leg of a **three-mechanism convergence family**:

| Mechanism | When | Role |
|---|---|---|
| **Hinted handoff** (this page) | write time, replica down | keep the write; redeliver on recovery |
| [[system-design-concepts/read-repair]] | read time | fix divergence on keys being *read* (hot data) |
| [[system-design-concepts/anti-entropy-merkle-trees]] | background | fix divergence on keys *never read* (cold data) |

Failure modes to name: a hint holder can itself die before handoff (the copy is lost — but you still had W−1 others, so durability holds if W was ≥2); and hints can pile up during a long outage, so systems **cap hint storage** and fall back to anti-entropy for anything past the window. Dynamo/Cassandra both do exactly this.

## What to actually memorize
1. **Down home replica ⇒ park the write on a healthy stand-in with a hint, count it to quorum, hand back on recovery.** = sloppy quorum.
2. It keeps **W durable copies** the whole time — availability without lowering W or losing durability.
3. It's what lets **RF=3 survive AZ+1** instead of ballooning RF.
4. It **creates drift** ⇒ must pair with **read-repair** (hot keys) + **anti-entropy** (cold keys).
5. Hints are **capped**; long outages fall through to anti-entropy.

## Key points
- Sloppy quorum (hinted) vs strict quorum (home-only) is the availability/consistency knob during failures.
- Preserves the durability target (still W copies) — distinct from "lower W," which sacrifices it.
- The reason replication factor stays sane (RF=3, not 6/9) for multi-AZ failure survival.
- Transient inconsistency is the explicit cost, delegated to read-repair + anti-entropy.
- A Dynamo-family primitive; assumes conflict resolution exists (version vectors / LWW) for when the hint and a later home write disagree.

## Interview angle

> "Hinted handoff is how a leaderless store stays writable when a replica or a whole AZ is down, without raising RF. If a home replica is unavailable, the coordinator writes that copy to the next healthy node tagged with a hint saying who it belongs to, counts it toward the write quorum, and that node hands the data back when the owner returns — sloppy quorum. The win is I keep W durable copies the whole time, so I get AZ-plus-one write availability at RF=3 instead of needing RF=9 for strict quorum. The cost is the copy is in the wrong place, so replicas are briefly divergent — which is why hinted handoff always ships with read-repair for hot keys and Merkle-tree anti-entropy for cold ones. And I'd cap hint storage so a long outage falls through to anti-entropy rather than growing unbounded."

## Connections
- [[theory/vector-clocks]] — decides supersede-vs-conflict when a hinted copy and a later home write meet on recovery
- [[system-design-concepts/read-repair]] — the read-time leg of the same convergence family; fixes drift on keys being read
- [[system-design-concepts/anti-entropy-merkle-trees]] — the background leg; fixes drift hinted handoff leaves on cold keys
- [[system-design-concepts/leaderless-vs-leader-based]] — hinted handoff is a defining feature of the leaderless (Dynamo) path; leader-based Raft doesn't need it
- [[theory/consistency-models]] — sloppy quorum breaks `W+R>N`'s intersection, pushing you toward eventual + reconciliation
- [[theory/durability-math]] — why AZ+1 survivability comes from this mechanism, not from a higher RF

## Sources
- [[sources/docs/distributed-kv-store-mock-interview]] — §6 hinted handoff (flagged), §4 sloppy vs strict quorum / AZ+1, §5 why-not-raise-RF
- [distributed-kv-store-mock-interview.md](https://github.com/redblackcoder/interview-prep-wiki/blob/master/sources/docs/distributed-kv-store-mock-interview.md) — full mock-interview design notes
- *Dynamo: Amazon's Highly Available Key-value Store* — DeCandia et al., SOSP 2007 (hinted handoff, sloppy quorum)
