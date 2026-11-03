---
source: interview session (raw transcript not published)
type: doc
date_extracted: 2026-09
topic: Interview Prep Wiki
secondary_source: code/combination-sum-k/ (https://github.com/redblackcoder/interview-prep-raw/blob/master/code/combination-sum-k/)
---

# Combination Sum + Restaurant Reservation — Coding + Design Round

A two-part session: (1) a coding problem — combinations from {1..9} summing to a target, with and without a fixed count k; (2) a 5-6 min mini system design — single-restaurant reservation. Seeds one backtracking coding-pattern page and one booking-concurrency design page.

## Key Ideas

**Coding — combination sum over a fixed set:**
- Backtracking on the include/exclude decision tree over candidates 1..9, carrying a running sum so the partial isn't re-summed.
- Base-case ordering is the trick: check `(size==k && sum==n)` first to record the hit; then prune on `(next==10 || size==k)`. Reaching k always terminates a branch (recorded if the sum is right, abandoned otherwise) — no overshoot.
- Complexity framing is the real content: with a fixed set it's genuinely O(1) (bounded input, at most 2^9 subsets); generalized to size N it's O(k * C(N,k)) inside an O(2^N) traversal. "Linear in k" is only the per-result copy cost (`new ArrayList<>(curr)` + print), not the algorithm's total.

**Design — single-restaurant reservation:**
- Model: Table(id, max_capacity), Reservation(id, start, end, table_id).
- Interval overlap in one predicate: two intervals overlap iff `s1 < e2 AND e1 > s2` — covers all four overlap cases.
- Two-step booking (find-available -> reserve) has a double-booking race between the steps.
- The design axis is **lock granularity**: coarse `SELECT ... FOR UPDATE` over all candidates (correct, serializes bookings, slow) -> single-row lock on the chosen table -> a DB **unique/exclusion constraint** that rejects the conflicting insert atomically (no explicit lock at all).

## My Understanding

- The generalized cost (O(k * C(N,k))) is the answer worth giving; "constant" is still true, but only as a footnote on top of it.
- Walk the lock-granularity spectrum cleanly: coarse row-range locks at one end, a declarative unique/exclusion constraint at the other. The constraint end is both the finest and the simplest — the DB enforces the invariant atomically — so name it as the target and treat locking as the fallback when a constraint can't express the rule.
- Booking reduces to **an invariant under concurrency** (no double-book), and the clean answer is declarative (a unique constraint) rather than lock-based.

## Open Questions

- Postgres exclusion constraint on `(table_id, tstzrange)` with a GiST index — exact syntax, and how it composes with capacity matching (which is a range filter, not an equality)?
- For the coding problem, is a cleaner iterative/DP formulation worth having ready, or is backtracking the expected answer given the tiny fixed input?

## Connections

- Relates to: [[wiki/coding-patterns/subset-enumeration-backtracking]] — the include/exclude decision-tree pattern (new page from this problem)
- Relates to: [[wiki/algorithms/knapsack-variants]] — combination-sum is a bounded subset-sum; same decision-tree shape, different objective
- Relates to: [[wiki/system-design-concepts/double-booking-prevention]] — booking concurrency framed as a lock-granularity spectrum (new page)
- Relates to: [[wiki/system-design-concepts/hot-key-write-contention]] — booking one popular table is the same write-contention shape
- Relates to: [[sources/docs/keyed-task-executor-interview]] — another problem that reduces to an invariant under concurrency
