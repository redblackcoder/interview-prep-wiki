# Distributed Unique ID Generation

How to hand out **globally unique** identifiers at high throughput, across regions, with **no central bottleneck** and **without remembering a single ID you've issued.** The whole discipline is one move: replace *coordination on every ID* with *uniqueness guaranteed by construction*, and shrink the coordination that remains to a rare event (once per process, at startup).

## The one-sentence mental model

> **Don't remember which IDs are taken — partition the ID space so that no two generators *can* produce the same ID, then let each generator run purely locally.**

Uniqueness-by-remembering (store every issued ID, check before minting) turns every generation into a read+write against an ever-growing set — it fights throughput and needs a durable P0 store. Uniqueness-by-construction turns it into an in-memory increment.

## The load-bearing decision: it's a library, not a service

The strongest architectural stance is that a cloud-scale ID generator should **not** be a network service every write calls. A central "ID service" (or a Redis `INCR`) adds three things to the hot path you don't want:

1. a **network round trip** per ID (even in-DC ~200µs, cross-AZ ~1ms — lethal against a ~5ms p99 SLA),
2. a **scaling bottleneck** (one shared counter),
3. a **stateful P0 dependency** *more critical than anything using it* — if it's down, nothing in the region can create a resource.

Instead: generate locally, in a library or host-local sidecar. The **only** external dependency is worker-id assignment, and that happens **once at process startup**, never per ID.

## The ID layout (Snowflake shape)

Partition a fixed bit budget into disjoint namespaces so collision is impossible by construction:

```
64-bit example (fits a BIGINT — good DB key):
[ timestamp ~41 bits (ms) ] [ region+worker ~10 bits ] [ sequence ~12 bits ]
   ~69 yrs from custom epoch    up to 1024 generators      4096 ids / ms / worker
```

Each field kills one collision axis:

| Field | Guarantees uniqueness across… | Notes |
|---|---|---|
| **timestamp (ms)** | *time* | 41 bits ms = ~69 yrs. Makes IDs **k-sorted** (index-locality win). |
| **region + worker** | *space* (different generators) | Two generators never share this ⇒ can't collide even in the same ms. |
| **sequence** | *within one worker, one ms* | Plain in-memory counter; overflow ⇒ spin to next ms. |
| **version** (optional) | *schema evolution* | A few bits so the layout can change without a flag day. |

**Bits vs. encoding are two layers.** Generate *bits*; *print* them separately. Lowercase-alphanumeric case-insensitive = **base32 = 5 bits/char** (not 4). 16 chars × 5 = 80 bits of payload rendered greppable. Never let the print format shrink your uniqueness space.

**Don't panic on the timestamp math.** ms, not µs/ns. `41 bits ms ≈ 69 years`; pair coarse ms with a per-worker sequence to disambiguate sub-ms. Reaching for µs resolution ("10^13 → too many bits → drop time") is the classic wrong turn — it throws away the k-sortability that motivated a timestamp in the first place.

## The coordination that remains: worker-id leasing

`region` is static config. `worker` is **leased**, not assigned forever, from a small HA coordinator (etcd / ZooKeeper):

- On startup a process **leases** a worker id (etcd lease + TTL, or ZK ephemeral/sequential znode).
- A **heartbeat** keeps the lease fresh; on clean shutdown the id is returned; on crash the lease **expires** and the id is reclaimed.
- This bounds the number of live workers to the id space (e.g. 1024) — so autoscaling churn doesn't exhaust it, *provided* reclaim is safe (see below).
- Fetching a worker id is **off** the generation path ⇒ a central dependency here is fine.

**API:** `generate(n) -> [id...]`. Batching lets a smart client cache a run of ids in memory and prefetch when low — amortizing even the sidecar hop to ~zero.

## The two failure modes that actually matter

Uniqueness is the one invariant whose violation is **silent and unrecoverable** (a duplicate primary key found weeks later). Both hard risks are ways the "by construction" guarantee quietly breaks.

### Risk 1 — the clock going backward (the #1 killer)

The timestamp assumes time only moves forward. NTP step corrections, VM live-migration, and leap seconds move wall clocks **backward by milliseconds** routinely. A worker at `t=1000` yanked back to `t=995` will **re-issue** ids from that window.

**Split it into two sub-problems — they have different solutions, and conflating them is what drags you toward a hot-path disk flush.**

**A — regression *while the process is alive* (NTP slew, leap-second smear, small step). Zero disk needed.**
- Seed from the wall clock **once at startup**, then drive the timestamp field from a **monotonic clock** (`CLOCK_MONOTONIC` / `System.nanoTime`). A monotonic clock by definition never regresses, so NTP moving the wall clock underneath you can't move your ids.
- Keep an **in-memory** `last_issued_ms` watermark:
  - `now == last` → increment sequence; on overflow, **spin-wait** to next ms.
  - `now < last`, **small** regression (< threshold) → keep minting against `last` (wait for the clock to catch up).
  - `now < last`, **large** regression → **stop minting, fail loud, alert.** Reject a few retryable writes rather than silently corrupt data. Blast radius = one worker; the client retries onto a healthy one.
- In-memory watermark + monotonic source fully covers A. No flush, ever.

**B — the watermark is *lost across a crash/restart*. This is the only part that ever wanted durability — and local disk is the wrong tool.**

> **Don't put a second durable store (local disk, 2–3 ms flush) on the hot path. You already have a durable, consistent, off-hot-path store: the etcd/ZooKeeper worker-id lease. Push restart-durability into the coordination you do at startup anyway.**

The elegant part: **the reclaim delay you already need for Risk 2 (split-brain) also solves B for free.** State the fleet assumption explicitly — *clocks are bounded by `max_clock_skew` (NTP-monitored; a host exceeding it self-ejects)* — then:

```
Worker A holds id 42; its lease expires (per etcd) at time E. A self-fences on
lease loss, so A's largest possible timestamp ≤ E + max_clock_skew.
Id 42 is reclaimed only after lease_TTL past E, so when B acquires 42, B's clock
already reads ≥ E + lease_TTL − max_clock_skew.

If  lease_TTL > 2 × max_clock_skew,  then  B's clock > A's max timestamp
BY CONSTRUCTION — before B mints a single id. No disk, no startup wait.
```

- Size **`lease_TTL ≥ 2 × max_clock_skew + margin`** and the cross-restart clock hazard disappears — the split-brain cooldown does double duty.
- Sharp edge: a **fast restart** (pod bounces in 500 ms) must not resurrect its old id inside the cooldown. Rule: **every process start is a cold claimant** — acquire a *fresh* lease (new fencing token), never fast-path re-acquisition of a still-cooling id. It then gets either a different id (disjoint `(timestamp, worker)` space → safe) or one that already cleared the delay (safe by the inequality).

**If you genuinely want an explicit persisted watermark (belt + suspenders, e.g. tiny id space): reserve-ahead, never record-behind.** This is the generalization of the gap-vs-replay rule.
- **Record-behind** (persist `last_issued` *after* minting) → on crash you resume from stale state and **replay** issued ids. *Unsafe — the trap.*
- **Reserve-ahead** (persist a ceiling *before* minting) → on crash you resume *above* the reservation. **Gap, never replay. Safe by construction.**
- Mechanism: one etcd write says "worker 42 may issue up to `now + Δ`" (Δ ≈ 2 s). Mint freely below the ceiling with **zero I/O per id**; an async task bumps the reservation before you approach it. Coordination cost is **one write per Δ, off the hot path** — not per id — and gaps are free (nobody needs contiguity).

**Bottom line: the 2–3 ms flush never enters the picture.** In-life regression is a *clock-source* problem (monotonic); cross-restart regression is a *coordination* problem you've already paid for.

### Risk 2 — worker-id split-brain (detection ≠ fencing)

The trap: worker leases id `42`, suffers a **30s GC pause / etcd partition**, its lease **expires**, etcd hands `42` to a new pod — and the paused worker **wakes up still believing it's 42** and mints locally (possibly from a client-cached batch, never touching the load balancer). Two live workers, same id, same region → collision.

Removing an unhealthy worker from the LB is **not** a fence — generation is *local*, so the authority must live *in the worker*:
- The worker must **self-check lease validity and stop minting fail-closed** the instant it can't confirm it still holds the lease. (Fail-closed, not fail-open.)
- A reclaimed id must not be reissued until `lease_TTL + max_clock_skew + safety_margin` has elapsed.

> **Rule:** when generation is local, fencing must be local. LB/health-check eviction only handles the *cooperative* failure; the pause/partition case needs the worker to gate itself.

## Why not just UUIDs? (the tradeoff to name)

| Scheme | Coordination | Sortable? | Size | Enumerable? |
|---|---|---|---|---|
| **UUIDv4** (random) | none | ✗ (kills B-tree locality) | 128b | no ✓ |
| **UUIDv7** (ms prefix + random) | **none** | ✓ k-sorted | 128b | no ✓ |
| **Snowflake** (this page) | worker-id at startup | ✓ k-sorted | **64b** (BIGINT) | **yes** ✗ |

Honest lean: if 128-bit keys are acceptable, **UUIDv7 is often the better default** — it deletes the entire worker-id problem (no lease, no fencing, no clock-skew-collision, since the tail is random). Choose **Snowflake** when you need a 64-bit BIGINT key or want decodable metadata; you pay for it with the two failure modes above.

**Redis `INCR`-per-ms-key** is a *correct* middle design (create-if-absent keys mean a standby generator needs no state from the dead leader — no split-brain), but it reintroduces the network hop and a stateful P0 dependency, so it's rarely the *best* one. Naming "correct ≠ best" here is the tradeoff signal.

## Security: sortable + external = enumerable

Snowflake ids leak **creation time and volume** to anyone who sees them in a URL, and are trivially enumerable. Close the loop explicitly: either these are **internal-only** (accepted), or **external surfaces get an opaque/random-tail variant** (e.g. UUIDv7, or encrypt the id). Sortability and non-enumerability are in direct tension.

## What to actually memorize
1. **Uniqueness by construction, not by remembering** — partition the space; never store issued ids.
2. **Library, not a service** — no per-id network hop, no per-id P0 dependency; coordinate **once at startup**.
3. Layout = **time (k-sort) | region+worker (space) | sequence (intra-ms)**; bits ≠ print encoding (base32 = 5 b/char).
4. **Clock-backward** = two problems: *in-life* ⇒ monotonic clock + in-memory watermark (no disk); *across-restart* ⇒ solved free by the split-brain reclaim delay if `lease_TTL ≥ 2·max_skew`. Never flush per id; if you must persist, **reserve-ahead** (gap), never **record-behind** (replay).
5. **Worker-id split-brain** ⇒ self-fence fail-closed in the worker; reclaim only after `TTL + skew + margin`. LB eviction is not a fence.
6. **UUIDv7** deletes the whole worker-id/clock problem if you can afford 128 bits; Snowflake buys you 64-bit + decodability.

## Interview angle

> "The core move is uniqueness by construction rather than by remembering: I partition the id space so two generators can't collide, then generate locally with no per-id coordination. So it's a library, not a service — a central id service or a Redis INCR puts a network hop and a stateful P0 dependency on every write, which is exactly what you can't afford on the hot path. Layout is `[ms timestamp | region+worker | sequence]` — time gives k-sortability, region+worker gives spatial disjointness, sequence disambiguates within a millisecond; 41 bits of ms is ~69 years. Worker ids are leased from etcd once at startup, off the hot path. The two things that actually break uniqueness are the clock going backward and worker-id split-brain. Clock-backward is really two problems: while the process is alive I drive the timestamp from a monotonic clock so NTP can't regress it, with an in-memory watermark that spins on small regressions and fails loud on large ones — no disk. Across a restart the watermark is lost, but I don't reach for local disk; that flush is 2–3 ms on the hot path and I already have a durable off-path store in the worker-id lease. If I size `lease_TTL ≥ 2× max clock skew`, the reclaim delay I need for split-brain anyway guarantees a new holder's clock is already past the old holder's last timestamp, so id reuse is safe with no disk and no startup wait. Split-brain itself: on a GC pause the worker has to fail-closed and stop minting the instant it can't confirm its lease, because eviction from the load balancer doesn't stop a local generator. If 128-bit keys are fine, UUIDv7 is honestly the better default since it deletes the worker-id and clock problems entirely; I'd pick Snowflake when I need a 64-bit BIGINT key or decodable metadata."

## Connections
- [[theory/consensus-raft]] — what backs the etcd/ZooKeeper worker-id lease; linearizable lease grant is why two workers can't hold the same id (absent the fencing gap)
- [[theory/latency-numbers]] — the ladder (in-DC ~200µs, cross-AZ ~1ms, cross-region ~150ms) that forces local generation over a central service
- [[theory/consistency-models]] — "uniqueness" is the strong-consistency requirement here; partitioning satisfies it locally instead of via global agreement
- [[system-design-concepts/hash-vs-range-partitioning]] — k-sorted ids give cheap range scans by creation time (same range-locality trade-off as range partitioning) but risk a write-hot tail
- [[system-design-concepts/work-distribution]] — worker-id leasing is the same "partition a bounded slot space across a churning fleet without overlap" problem
- [[system-design-concepts/leaderless-vs-leader-based]] — the Redis-leader vs. leaderless-worker-id choice mirrors the write-path fork
- [[tech/aws-elasticache-redis]] — the `INCR`-per-ms-key alternative and why its statefulness on the hot path is the drawback
- [[tech/istio-service-mesh]] — LB/mesh retry gives caller-side availability on worker failure, but is *not* a uniqueness fence (Risk 2)

## Sources
- Mock design interview — "Design a distributed unique ID generator at Claude for a cloud-scale service" (Rippling L8 systems-design prep, 2026-08-17): scope-down of trace/log ids, persistence retraction, Redis-vs-worker-id tradeoff, clock-backward + worker-id-fencing deep dives.
- [distributed-id-generator-mock-interview.md](https://github.com/redblackcoder/interview-prep-raw/blob/master/docs/distributed-id-generator-mock-interview.md) — full mock-interview transcript + scorecard (raw repo)
- *Announcing Snowflake* — Twitter Engineering, 2010 (the `time | machine | sequence` layout).
- **RFC 9562** — Universally Unique IDentifiers (UUIDs), 2024 (UUIDv7: ms-prefix, k-sortable, coordination-free).
