# Knapsack Variants — 0/1, Bounded, Unbounded

All three knapsack variants build the same pseudo-polynomial `O(n·W)` DP table — `dp[i][j]` = best value using the first `i` items within capacity `j`. What separates them is a single question: **how many copies of item `i` may you take?** The naive answer — an inner loop over "0, 1, 2, … copies" — works for all three but is the *unoptimized baseline*. The substance of each variant is precisely the trick that eliminates that inner loop.

## The three variants

| Variant | Copies of item *i* allowed |
|---|---|
| **0/1** | at most 1 |
| **Bounded** | at most `c_i` (a given finite count) |
| **Unbounded** | unlimited |

Bounded generalizes both extremes: `c_i = 1` *is* 0/1; and once `c_i ≥ ⌊W/w_i⌋` the cap can never bind, so that item behaves as unbounded.

## 0/1 vs unbounded: same body, opposite loop direction

The distinction is *which row the transition reads from* — and in the space-optimized 1D form, that collapses to loop direction.

**0/1** reads the *previous* row, so each item is consumed once:
`dp[i][j] = max(dp[i-1][j], dp[i-1][j-w] + v)`

**Unbounded** reads the *same* row, so after taking one copy you may take more — collapsing the "try all `k` copies" loop into O(1) per cell:
`dp[i][j] = max(dp[i-1][j], dp[i][j-w] + v)`   ← note `dp[i]`, not `dp[i-1]`

1D form — **identical bodies, opposite capacity direction**:

```python
# 0/1: capacity HIGH -> LOW   (dp[j-w] still = previous row => each item once)
for i in range(n):
    for j in range(W, w[i]-1, -1):
        dp[j] = max(dp[j], dp[j-w[i]] + v[i])

# Unbounded: capacity LOW -> HIGH   (dp[j-w] already includes item i => unlimited)
for i in range(n):
    for j in range(w[i], W+1):
        dp[j] = max(dp[j], dp[j-w[i]] + v[i])
```

High→low keeps `dp[j-w]` on the previous row (item not yet used this pass). Low→high lets `dp[j-w]` already include item `i`. **That one flip is the entire 0/1-vs-unbounded difference** — no inner count loop required. Both are `O(n·W)`.

## Bounded (`c_i` copies): the genuinely harder middle

Here the "try all `k`" loop *does* apply — `dp[i][j] = max over 0 ≤ k ≤ c_i of dp[i-1][j - k·w] + k·v` — but it costs `O(n·W·max c_i)`, bad when counts are large. Two standard optimizations replace the inner loop:

**Binary splitting** — decompose `c_i` into powers of two plus a remainder (e.g. `13 → {1, 2, 4, 6}`), scale each chunk's weight/value by its size, and treat each chunk as a single 0/1 item. Any `k ∈ [0, c_i]` is a subset of the chunks, so ordinary 0/1 over `Σ log c_i` items gives `O(n·W·log C)`. Easy to code; usually enough.

**Monotonic deque (sliding-window max)** — fix item `i`, group cells by residue class `j mod w_i`. Writing `j = r + t·w`:
`dp[i][j] = t·v + max over (t - c_i ≤ s ≤ t) of ( dp[i-1][r + s·w] − s·v )`
That's a width-`c_i` sliding-window maximum of the term `dp[i-1][…] − s·v`, answered in amortized O(1) by a monotonic deque → optimal `O(n·W)`. More code, but asymptotically best.

## When to use
- **0/1** — each item is unique (subset-sum, partition, "pick items"): 1D array, iterate capacity **high→low**.
- **Unbounded** — unlimited supply (coin change, rod cutting, "cut/combine to a target"): 1D array, iterate capacity **low→high**.
- **Bounded** — limited stock per item: if any `c_i ≥ ⌊W/w_i⌋`, treat that item as unbounded; otherwise binary-split (simple) or use a monotonic deque (optimal).
- **The tell**: if you're writing an inner "for each count `k`" loop, you've written the naive version — reach for the same-row recurrence (unbounded), the loop flip (0/1), or splitting/deque (bounded).

## Interview angle
> "All three knapsacks are the same `O(nW)` table; the only question is how many copies per item. The elegant fact is that 0/1 vs unbounded is *just loop direction* in the 1D array — capacity high-to-low reads the previous row so each item is used once, low-to-high reads the same row so you get unlimited copies. No inner count loop needed. Truly bounded — a cap `c_i` per item — is the real work: I'd binary-split each count into powers of two and run 0/1 for `O(nW log C)`, or use a monotonic-deque sliding-window max per residue class for optimal `O(nW)`. And if a cap exceeds `W/w`, it can't bind, so I treat that item as unbounded."

## Connections
- [[coding-patterns/fold-accumulator]] — the 1D `dp[]` is a state accumulator threaded across items in a single pass; same "thread state, don't re-derive it" instinct
- [[theory/folds-and-tail-recursion]] — DP tabulation is memoized recursion turned into a loop; the recursion→iteration move that page describes
- [[algorithms/dynamic-programming]] — *(future hub)* memoization vs tabulation, state design, pseudo-polynomial complexity

## Sources
- Distilled from a conceptual Q&A session (2026-09-06); no raw source extract yet. Run `/extract` to formalize one if a worked knapsack problem is added under `raw/`.
- Canonical treatment: CLRS *Introduction to Algorithms*, the Dynamic Programming chapter — rod-cutting is the textbook unbounded-knapsack sibling; the 0/1-vs-fractional knapsack distinction lives in its Greedy chapter.
