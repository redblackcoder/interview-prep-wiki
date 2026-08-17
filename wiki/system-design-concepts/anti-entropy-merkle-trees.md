# Anti-Entropy with Merkle Trees

The **background** convergence mechanism: replicas periodically compare their data and repair any divergence, *including keys no one is reading*. The naive version — ship everything and diff — costs a full data transfer. **Merkle trees** make it cheap: two replicas exchange a tree of hashes and drill down only into the sub-ranges that actually differ, so the network cost scales with the *amount of divergence*, not the size of the dataset.

## The one-sentence mental model

> **Hash every key-range into a tree of hashes; two replicas compare roots — equal means identical, done in one message — and when roots differ they recurse only into mismatching children, so they exchange O(differences · log N), not O(N).**

## Why it exists

[[system-design-concepts/read-repair]] only heals keys that get **read**; [[system-design-concepts/hinted-handoff]] only covers failures it saw at **write** time and can drop hints after a long outage. A key that was written, missed by a replica, and never read again would stay divergent forever. Anti-entropy is the **backstop that guarantees eventual convergence** for the entire keyspace — the cold tail the other two legs miss.

```
 convergence family (leaderless store):
   hinted handoff   → write-time, replica down     → keep the write
   read repair      → read-time divergence         → hot keys
   anti-entropy     → background, ALL keys          → cold keys / long-outage drift  ← this page
```

## A Merkle tree

A **Merkle tree** is a binary tree where each **leaf** hashes one bucket of keys and each **internal node** hashes the concatenation of its children's hashes. The root is a fingerprint of the entire dataset.

```
                 root = H(H_L ‖ H_R)
                /                    \
        H_L=H(a‖b)                H_R=H(c‖d)
        /       \                 /        \
     a=H(k0..)  b=H(k1..)     c=H(k2..)  d=H(k3..)     ← leaves hash key-range buckets
```

Two properties do all the work:
1. **Equal roots ⇒ identical data.** One hash comparison certifies a whole replica in sync — the common case (no divergence) costs *one* message.
2. **A change propagates only up its own path.** Edit a key in bucket `c` → `c`, `H_R`, and `root` change; `H_L`, `a`, `b` don't. So a diff is localized to one root-to-leaf path.

## The sync protocol

Two replicas responsible for the same range compare trees top-down, descending **only into mismatched subtrees**:

```
 compare roots ─ equal? ─▶ DONE (whole range in sync, 1 message)
        │ differ
        ▼
 compare children:  H_L equal → skip entire left half
                    H_R differ → recurse right
        ▼
 …down to the differing leaf buckets → exchange only those keys → repair
```

Cost is **O(d · log N)** for `d` divergent buckets over `N`, versus **O(N)** to ship everything. When replicas are identical (the overwhelmingly common case), it's a **single root-hash comparison.** That asymmetry — near-free when in sync, cheap-and-localized when not — is why it's the standard.

## Costs and real-world detail

- **Tree maintenance:** the tree must track live data. Rebuilding on demand means reading the whole range (expensive), so systems maintain it incrementally or rebuild during compaction. Stale trees cause missed or redundant repair.
- **Granularity trade-off:** shallow tree (few big leaves) → cheap tree, but any diff drags a big bucket over the wire. Deep tree (many small leaves) → precise diffs, but a bigger tree to store/compare. Tune leaf size to expected divergence.
- **Range alignment:** both replicas must bucket the **same key ranges** or every leaf mismatches. Cassandra scopes trees to token ranges; **vnodes multiply the number of small trees** to build and compare (another cost of high vnode counts — see [[theory/consistent-hashing]]).
- **Cadence:** too frequent wastes CPU/IO hashing unchanged data; too rare widens the window where a lost replica's data is under-replicated (raising effective MTTR — see [[theory/durability-math]]). Cassandra runs it as scheduled **repair**; Dynamo used it between replica pairs.

Merkle trees show up with the same "compare hashes, fetch only diffs" logic in Git (commit/tree objects), ZFS/Btrfs scrub, and blockchains — worth name-dropping to show the pattern generalizes.

## What to actually memorize
1. **Background leg** that converges **all keys**, incl. the cold ones read repair/hinted handoff miss → guarantees eventual convergence.
2. **Merkle tree = tree of hashes**; leaves hash key-range buckets, parents hash children, root fingerprints everything.
3. **Equal roots ⇒ in sync in one message**; differ ⇒ recurse only mismatched subtrees → **O(diffs · log N)**, not O(N).
4. Trade-offs: **leaf granularity** (precision vs tree size), **range alignment** (must bucket identically), **cadence** (CPU vs convergence window), and **stale-tree** maintenance.
5. Same pattern as Git / ZFS scrub / blockchain state proofs.

## Key points
- The completeness backstop of the convergence family — without it, unread divergence is permanent.
- Merkle diffing makes reconciliation cost scale with *divergence*, not dataset size; the in-sync case is one hash compare.
- Granularity, range alignment, tree-freshness, and cadence are the four knobs; vnodes multiply the tree count.
- Repair cadence interacts with durability: it's part of how fast under-replicated data is restored.
- General-purpose structure (Git, filesystems, blockchains), not KV-specific — signals pattern literacy.

## Interview angle

> "Anti-entropy is the background leg that guarantees convergence for every key, including cold ones read repair never touches because nobody reads them. The naive version ships all the data and diffs it — O(N). Merkle trees fix that: each replica hashes its key-range buckets into leaves and hashes upward to a root, so if two replicas' roots match, the entire range is identical in a single message. When they differ, a change only affects the hashes on its root-to-leaf path, so they recurse just into mismatching subtrees and exchange O(differences · log N) — network cost scales with how much diverged, not how much data exists. The knobs are leaf granularity, keeping the tree fresh, aligning ranges between replicas, and cadence — too rare and under-replicated data lingers, raising effective MTTR. It's the same hash-tree diffing Git and ZFS use."

## Connections
- [[system-design-concepts/read-repair]] — the read-time leg; anti-entropy covers the cold keys it can't reach
- [[system-design-concepts/hinted-handoff]] — the write-time leg; anti-entropy is the fallback when hints are dropped after a long outage
- [[theory/consistent-hashing]] — trees are scoped to token ranges; high vnode counts multiply the number of trees to maintain
- [[theory/durability-math]] — repair cadence is part of MTTR — how fast under-replicated data returns to full RF
- [[system-design-concepts/leaderless-vs-leader-based]] — anti-entropy is a leaderless necessity; a Raft log keeps replicas from diverging in the first place
- [[theory/bloom-filters]] — sibling "compare compact digests instead of shipping data" idea for set reconciliation

## Sources
- [[sources/docs/distributed-kv-store-mock-interview]] — §6 anti-entropy / Merkle-tree sync (flagged), §4 convergence after sloppy quorum
- [distributed-kv-store-mock-interview.md](https://github.com/redblackcoder/interview-prep-wiki/blob/master/sources/docs/distributed-kv-store-mock-interview.md) — full mock-interview design notes
- *Dynamo: Amazon's Highly Available Key-value Store* — DeCandia et al., SOSP 2007 (anti-entropy via Merkle trees)
