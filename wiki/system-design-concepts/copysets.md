# Copysets (Constrained Replica Placement)

If every chunk or extent picks its 3 replica nodes at random, then with millions of chunks, **almost every possible triple of nodes holds some chunk**. So *any* simultaneous loss of 3 nodes (a rack power event, a bad kernel rollout, a cluster restart) almost surely loses data. Copyset replication (Cidon et al., USENIX ATC 2013) restricts placement to a small, deliberate set of node groups. You lose data far less often, at the price of slower repair.

## The one-sentence mental model

> **Durability against correlated failure depends on how many distinct replica groups (copysets) your placement creates. Random placement creates nearly all of them. Constraining placement to a few rack-diverse groups makes a correlated 3-node loss rarely fatal. The knob is scatter width: more peers per node means faster repair but more copysets.**

## The math (N = 100 nodes, RF = 3)
- Possible triples: C(100, 3) = **161,700**.
- **Random placement**, 10M extents: ~62 extents per triple, so P(a given triple holds nothing) ≈ e^-62 ≈ 0. **Any 3 dead nodes ≈ 100% chance of loss.**
- **Copyset replication:** with scatter width S (the number of other nodes each node shares data with), you need about `(S / (R−1)) × (N / R)` copysets.

| Placement | Copysets | P(loss \| 3 random nodes die) | Rebuild 300 TB at 200 MB/s per peer |
| --- | --- | --- | --- |
| Random (S ≈ 99) | ~all 161,700 | ~100% | ~4 h (≈100 peers) |
| Copyset, S = 50 | 25 × 33 = 825 | ~0.5% | ~8 h |
| Copyset, S = 10 | 5 × 33 = 167 | ~0.1% | ~42 h |
| Mirrored triples (S = 2) | 33 | ~0.02% | ~9 days (2 peers) |

- The trade-off is real in both directions. Fewer copysets mean loss events are **rarer but bigger** (when a copyset dies you lose all of its data), and repair has **fewer sources**, so MTTR rises, which hurts independent-failure durability ([durability math](../theory/durability-math.md)). Pick S so the MTTR stays in hours.

## How it works in a design
- Build copysets offline. Take S/(R−1) random permutations of the nodes and chop each into groups of R. Make every member of a group come from a different rack or power domain.
- The metadata service places an extent by choosing **a copyset** (weighted by free capacity), then best-fitting within its 3 nodes. That keeps the flexibility of per-extent placement.
- Repair and rebalancing must keep the constraint: a replacement for a dead member stays within a valid copyset, or the copysets get rebuilt as nodes join and leave.

## Key points
- Random per-object placement is the default in naive designs and in many real systems. It's the *reason* "we lose data on every rack failure" happens at scale.
- Copysets are the single-DC answer to "rack-aware placement": correlated failures happen within a DC (racks, power, rollouts), not only across AZs.
- It's orthogonal to RF. RF=4 with random placement still lets almost any 4-node event lose data.
- Similar ideas: HDFS rack-aware placement (1 local rack + 2 on a remote rack), tiered replication, and "chain sets" in some object stores.

## Interview angle
> "Per-extent random placement means almost every node triple shares an extent, so any correlated 3-node loss loses data. I constrain placement to rack-diverse copysets and best-fit within one. With a scatter width around 50 on 100 nodes, a random 3-node loss is fatal about 0.5% of the time instead of about 100%, and a dead node still rebuilds from about 50 peers in hours."

## Connections
- [durability math](../theory/durability-math.md): the correlated-failure ceiling this addresses, and the MTTR term it trades against
- [replica repair and re replication](replica-repair-and-re-replication.md): scatter width sets rebuild parallelism
- [distributed file system](distributed-file-system.md): placement step of the put path
- [consistent hashing](../theory/consistent-hashing.md): hash-ring successor placement is another way to bound the number of replica sets

## Sources
- [lakehouse file storage](../problems/system-design/lakehouse-file-storage.md): per-extent replication → copyset risk
- Background, not ingested: Cidon et al., "Copysets: Reducing the Frequency of Data Loss in Cloud Storage", USENIX ATC 2013
