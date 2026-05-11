# Bloom Filters

A space-efficient probabilistic data structure that tests whether an element is a member of a set. It can produce false positives (says "yes" when the element isn't there) but never false negatives (if it says "no", the element is definitely absent).

## How it works

1. Allocate a bit array of size m, initialized to 0.
2. Choose k independent hash functions, each mapping to a position in [0, m).
3. **Insert(x)**: Hash x with all k functions, set those k bits to 1.
4. **Query(x)**: Hash x with all k functions. If ALL k bits are 1, return "probably yes". If ANY bit is 0, return "definitely no".

### Tuning
- **False positive rate** ≈ (1 - e^(-kn/m))^k where n = items inserted.
- Optimal k = (m/n) × ln(2).
- For 1% false positive rate: ~10 bits per element, 7 hash functions.

## Key points
- **No deletions** in basic bloom filters (can't unset a bit shared by other elements). Counting bloom filters use counters instead of bits to support deletion.
- **Space efficiency**: Orders of magnitude smaller than storing the actual set. For 1B URLs at 1% FPR: ~1.2 GB vs. tens of GB for a hash set.
- **Distributed use**: Each crawler shard maintains its own bloom filter for URLs it has seen. Can be merged (OR the bit arrays) for global dedup checks.
- **No false negatives guarantee** makes it perfect for dedup: if the filter says "not seen", you can safely fetch. A false positive only means an occasional skipped URL — acceptable.

## Interview angle

> "Bloom filters give you set membership with zero false negatives in sub-linear space. For URL dedup in a crawler: ~10 bits per URL, 1% false positive rate. A false positive means we skip a URL we haven't seen — tolerable. A false negative would mean duplicate work — and that never happens."

## Connections
- [[system-design-concepts/web-crawler]] — URL dedup across distributed crawler fleet
- [[system-design-concepts/work-distribution]] — redundancy avoidance via probabilistic dedup

## Sources
- [[sources/docs/web-crawler-system-design]] — bloom filter for preventing duplicate URL fetches across shards
