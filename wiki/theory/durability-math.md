# Durability Math: From Disk AFR to Nines

"Six nines of durability" is a number you must be able to *derive*, not just quote. The exercise: given a disk failure rate and a replication factor, compute the probability the system loses a piece of data in a year — and, more importantly, discover **which knob actually moves the answer.** The punchline that separates a principal from a mid-level candidate: it isn't the replication factor.

## The one-sentence mental model

> **Data loss requires all RF replicas to fail within the same repair window, so durability is governed by how fast you re-replicate (MTTR), not by how many copies you keep (RF) — and past RF=3 the real ceiling is correlated failure, which more copies don't fix.**

## Definitions

- **AFR (Annualized Failure Rate)** — probability one disk/node dies in a year. Real-world disk AFR ≈ **1–2%** (Backblaze fleet data). Use 2% as a pessimistic default.
- **Durability** — probability a stored object is *not* lost over a year, expressed in nines: 6 nines = 99.9999% = **P(loss) ≤ 10⁻⁶ per object per year**.
- **MTTR (Mean Time To Repair)** — for durability this is **detect-death + re-replicate a full replica's data**, not "swap the disk." Hours, and *controllable*. This is the lever.
- **Replica set** — the RF nodes holding copies of a given key range. The fleet is thousands of independent sets.

## The derivation (RF=3, independent failures)

The whole derivation is one idea: **a replica dying is not a data-loss event — it starts a race.** The system runs to make a fresh copy; failure is only if the *other* replicas die before it wins that race. We build the number up one step at a time.

### Step 0 — the self-healing timeline

When a disk dies, the system detects it and copies that replica's data onto a healthy spare from the *surviving* replicas. Call the total detect-then-rebuild time **MTTR**. During that window you are down to RF−1 copies and vulnerable; after it, you're back to full RF and safe again.

```
 replica A: ──●━━━━━━━━━━━✕                         (A dies at t0)
                          │◀──── MTTR ────▶│
 rebuild:                 └─ copy A's data ─┴─▶ ● full RF restored
 replica B: ──●───────────────────────────────────  (B alive, a source)
 replica C: ──●───────────────────────────────────  (C alive, a source)
                          ▲                ▲
                        t0 = A dies     safe again
                    (vulnerability window opens)   (window closes)
```

The entire game is: **does a second (then third) failure land inside that shrinking window?** So MTTR is not a maintenance detail — it is the width of the danger zone, and it appears squared below.

### Step 1 — how often does the *first* replica die?

Per replica set of 3 disks, each with annual failure rate **AFR**, the expected number of first-failures per year is just `3 · AFR`. (AFR ≈ 0.02, so ≈ 0.06/yr — a first failure roughly every 16 years per set. Common, fleet-wide.) This is the **rate** the race starts.

### Step 2 — given A is down, will B die during the window?

B only threatens us if it dies in A's MTTR window, not any time this year. The window is a fraction of a year: `MTTR / 1yr`. With MTTR = 4 h, that fraction is `4 / 8766 ≈ 4.6×10⁻⁴`.

So the probability B dies *while we're exposed* is its annual chance scaled down to the window:

```
P(B dies in window) = AFR · (MTTR / yr) ≈ 0.02 · 4.6×10⁻⁴ ≈ 9.1×10⁻⁶
```

```
 A: ──✕ dead, rebuilding…
       │◀──────── MTTR ────────▶│
 B: ───────────✕                       ← only THIS window counts, not the whole year
       └────── exposed ─────────┘         P = AFR · (MTTR/yr)
```

### Step 3 — now down to one copy, will C die too?

If B dies mid-window we're at a **single copy** — lose C and the data is gone. C dying in the *remaining* window is, to the accuracy we care about, the same small factor again:

```
P(C also dies in window) ≈ AFR · (MTTR / yr) ≈ 9.1×10⁻⁶
```

```
 A: ──✕ rebuilding…
 B: ──────✕ rebuilding…           ← now only ONE copy (C) left alive
 C: ───────────✕  ✦ DATA LOST     ← third failure before any rebuild finished
       └─── both inside the window ───┘
```

### Step 4 — multiply the chain

Data loss = A starts the race **and** B dies in-window **and** C dies in-window. Multiply the rate by the two conditional probabilities:

```
P(loss / set / yr) ≈ (3 · AFR)  ×  (AFR · MTTR/yr)  ×  (AFR · MTTR/yr)
   ▲ start race        ▲ 2nd in window     ▲ 3rd in window
                    = 3 · AFR³ · (MTTR/yr)²
```

The shape is the whole lesson: **AFR³** (three disks must fail) and **(MTTR/yr)²** (the 2nd and 3rd each squeezed into the danger window). The `3` is just "which replica dies first" bookkeeping — the exponents carry the result.

> Aside on the constant: strictly, ordering and the shrinking remaining-window contribute a small factor (≈3–6). It doesn't change the order of magnitude, so we keep `3` and don't fuss — the AFR³·(MTTR/yr)² *shape* is what you defend at a whiteboard.

### Step 5 — plug in numbers

```
AFR = 0.02      MTTR = 4 h → MTTR/yr = 4/8766 ≈ 4.6×10⁻⁴

3 · AFR³           = 3 · (0.02)³      = 3 · 8×10⁻⁶   = 2.4×10⁻⁵
(MTTR/yr)²         = (4.6×10⁻⁴)²                     ≈ 2.1×10⁻⁷
─────────────────────────────────────────────────────────────
P(loss/set/yr)     = 2.4×10⁻⁵ · 2.1×10⁻⁷            ≈ 5×10⁻¹²
```

≈ 5×10⁻¹² loss per set per year → **~11 nines per replica set.**

### Step 6 — two different questions ("nines of what?")

Step 5 gives **per-set** loss. Scaling up, be precise about *which* durability you mean — they differ by the set count, so you must know where that count comes from.

**(a) Per-object durability — this is the SLA.** A given object lives on exactly *one* replica set, so its annual loss probability is just the per-set number — **~11 nines — independent of how big the fleet is.** When a provider advertises "eleven 9s," this is the metric: per object, per year. Fleet size is irrelevant here.

**(b) Fleet-wide P(*any* loss event this year) — an operational metric.** *Any* set losing its trio is an incident, so you multiply by the **number of distinct replica sets** — and that count comes from *placement*, not data volume (partitions sharing the same 3 nodes fail together = one risk unit):

```
no vnodes:     #sets ≈ N            → 100 nodes ≈ 10²  sets
with vnodes:   #sets ≈ up to N·V    → hundreds–thousands, ~10⁴ at heavy fan-out
```

```
our single-region cluster (~10² sets):
  P(any loss/yr) ≈ 10² · 5×10⁻¹² ≈ 5×10⁻¹⁰   → ~9 nines
large fleet / heavy vnodes (~10⁴ sets):
  P(any loss/yr) ≈ 10⁴ · 5×10⁻¹² ≈ 5×10⁻⁸    → ~7 nines
```

Both clear the 6-nines *object* SLA with margin. Note the subtlety this exposes — **spreading data over more distinct trios (more vnodes) raises P(some set fails)** even as it speeds rebuild and balances load: a real durability cost of vnodes, see [[theory/consistent-hashing]].

**Bottom line:** the customer-facing 6 nines is the **per-set ~11-nines number (Step 5), and it does not depend on fleet size at all.** The earlier "~7 nines across 10⁴ sets" was answering question (b) for a *hyperscale* fleet, and I'd stated the set count without deriving it — for our ~100-node region it's ~10² sets and ~9 nines. Either way: **RF=3 with fast re-replication is already a 6-nines design** — durability comes from *winning the MTTR race*, not from piling on copies.

## The two insights that fall out

**1. MTTR is the lever, not RF.** Loss scales as `(MTTR/yr)^(RF−1)`. Halving MTTR (faster failure detection, parallel rebuild from many peers, spare capacity ready) squares-down the loss probability — a far bigger win than adding a replica. AFR you cannot change (it's the hardware); MTTR you engineer. **This is why cloud stores obsess over re-replication speed.**

| Change | Effect on P(loss) |
|---|---|
| RF 3 → 4 | ×~AFR·(MTTR/yr) — another ~4–5 nines, but see insight #2 |
| MTTR 4 h → 1 h | ÷16 (quadratic in the RF=3 case) |
| MTTR 4 h → 24 h | ×36 — slow rebuild *destroys* durability |

**2. Beyond RF=3, correlated failure dominates — so more replicas stop helping.** The math above assumes *independent* failures. Reality has **correlated** ones: a bad disk batch, a rack/AZ power event, a bug that writes corrupt bytes to *every* replica, an operator `DROP`. These have their own floor (say 10⁻⁵–10⁻⁶) that **RF cannot touch** — RF=6 replicates the corrupting bug six times, faithfully. Once independent-failure loss (10⁻¹¹) is far below correlated loss, adding replicas improves a term that no longer matters.

The consequence, stated in the interview: **RF beyond 3 is an *availability* decision, not a durability one.** You raise RF to keep serving through more simultaneous node/AZ losses (quorum survival), not to lose less data.

## What actually gets you to 6 nines

- **RF=3 across 3 AZs** (independent power/network/cooling ⇒ decorrelates the AZ-level event).
- **Fast, automated re-replication** — the MTTR term; no human in the loop (recall 4–5 nines availability ≈ minutes/month, so a human paging in has already lost).
- **Synchronous replication to W replicas before ACK** — this is *operational* durability, RPO≈0 (see [[theory/durability-rpo-rto]]). An acked write is on W disks *now*.
- **Off-cluster backups (S3) for the correlated/logical failures the RF math can't cover** — bad-batch, corruption bug, operator error, whole-region DR, PITR. Backups are async ⇒ non-zero RPO ⇒ they are *not* operational durability; restoring TBs has an RTO that blows the availability budget. Different failure class, different tool.

## Key points
- 6 nines = P(loss) ≤ 10⁻⁶/object/yr — a target you derive from AFR, RF, and MTTR, not a slogan.
- `P(loss/set) ≈ 6·AFR³·(MTTR/yr)²` for RF=3 → ~11 nines/set, ~7 nines across a 10⁴-set fleet.
- **MTTR is the dominant, controllable lever** — loss ∝ `(MTTR/yr)^(RF−1)`; fast re-replication beats more copies.
- **RF=3 already clears 6 nines**; beyond it, *correlated* failure dominates, so extra replicas buy availability (quorum survival), not durability.
- Synchronous W-replica ACK = operational durability (RPO≈0); backups = defense against correlated/logical loss + DR, never a substitute.

## Interview angle

> "I derive it rather than quote it. Loss needs all RF replicas dead inside one repair window, so for RF=3 it's roughly 6·AFR³·(MTTR/year)² — with 2% AFR and a 4-hour MTTR that's about 1e-11 per replica set per year, ~7 nines even across ten thousand sets. So RF=3 already clears six nines. The lever isn't replication factor, it's MTTR — loss goes as MTTR to the (RF−1), and MTTR is the thing I actually control with fast detection and parallel rebuild. I only raise RF above 3 for *availability* — surviving more simultaneous failures on a quorum — because past three copies durability is capped by *correlated* failure, a bad batch or a corruption bug, which more replicas just copy faithfully. That correlated tail is what backups and multi-AZ are for, not extra RF."

## Connections
- [[theory/durability-rpo-rto]] — RPO≈0 (synchronous W-replica ACK) is *operational* durability; this page is the *statistical* durability that RF+MTTR buy
- [[theory/consistency-models]] — the sibling axis; W-replica ACK ties durability to the quorum/consistency choice
- [[system-design-concepts/rds-vs-key-value-store]] — "persisted cache ≠ durable store" is the same async-vs-sync-replication distinction quantified here
- [[system-design-concepts/cloud-database-cost-model]] — RF and spare-capacity-for-fast-MTTR are the storage-cost drivers; durability is bought with $
- [[theory/consistent-hashing]] — the partition scheme that decides *which* nodes form a replica set and how fast a dead one is rebuilt

## Sources
- [[sources/docs/distributed-kv-store-mock-interview]] — §5 durability math, §4 replication/quorum, §3 nines→downtime
- [distributed-kv-store-mock-interview.md](https://github.com/redblackcoder/interview-prep-wiki/blob/master/sources/docs/distributed-kv-store-mock-interview.md) — full mock-interview design notes
