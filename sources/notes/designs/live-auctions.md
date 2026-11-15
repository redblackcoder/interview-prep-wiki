# Live auctions

Anonymized candidate design notes.

*High level.* Stateless **auction service** behind an LB (any instance handles any request). **Auction detail** (metadata) in a **relational DB + cache**: cache for high-read/low-latency, RDB as system of truth for writes. Bids **rejected if outside the active window** (cheap early filter). A **daemon** runs an event loop over auctions that ended in the last *x* seconds and asynchronously runs end-of-life (winner) workflows.

*Bid path — the core.* Do **not** compare-and-set on the write. **Append every bid to a log partitioned by `auction_id`** → an ordered ledger per item in one partition (Kafka-like: high-throughput, low-latency appends). Auction service **batches heavily** for throughput. Bids carry **both client-side and server-side timestamps**. Stream the full ledger to a warehouse for **audit**.

*Deriving the max.* A **consumer reads the partition in batches**, computes the **max over the batch**, and **compare-and-sets the current highest bid into Redis** (`key = auction_id/item_id → highest bid`). At 10k bids/s a batch is scanned in-memory "in a blink." Read a **trailing 1-second window** (buffer off the current second). Auction service serves the live top bid from Redis, fresh to ~100s of ms.

*Scaling past one partition.* Because **max is aggregatable**, split a hot auction across multiple partitions (partition key includes an auction-server id → a group of servers writes one partition). Each consumer finds a **local max**; all CAS into the **single Redis key**. Batch at every layer; keep the final aggregator (Redis) at a sane rate.

*Clock skew.* Clients read the top value from Redis, so skew doesn't affect display. Skew bites at the **boundary**: did a bid land before close? Server clocks disagree, so **accept bids for a small grace window (~50 ms) after local end**; the recorded timestamps then adjudicate. The daemon triggers the close workflow ~50 ms after end so the ± skew window is considered. (Didn't detail the exact skew-reconciliation algorithm.)

*Failover (added late).* Run the auction in **two regions in parallel** — bids to both, both compute, active serves, passive takes over on outage; near-instant failover via Anycast.
