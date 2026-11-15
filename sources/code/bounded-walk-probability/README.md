# Bounded Random-Walk Survival Probability

**Source:** coding interview round (45 min).

## Problem

An **8×8×8** board holds a single piece. From `(x,y,z)` it moves diagonally to
`(x±1, y±1, z±1)` — **8 possible moves**. Starting from a given cell, make **N random
moves**, each chosen uniformly among the 8 directions (including directions that fall
off the board). Compute the probability that **all N moves stay on the board** (i.e. we
never step out).

Asked for P(not stepping out); P(step out) = 1 − that.

## Core idea

Let `f(cell, k)` = probability of surviving `k` moves from `cell`.

```
f(cell, 1) = validMoves(cell) / 8
f(cell, k) = sum over valid neighbors m of  (1/8) * f(m, k-1)
```

An off-board pick contributes 0, so only valid neighbors carry probability forward.

- **Naive recursion** over the move tree is **O(8^N)** (pruned below that by the boundary, still exponential).
- **Memoize on the bounded state `(cell, k)`** → `O(N · cells · 8) = O(N · 8^4) = O(N)`, since the board is a fixed 8×8×8. This is the whole optimization.

## Files

- `Solution.java` — three implementations that cross-check each other:
  - `surviveProbDP` — bottom-up DP over a `[8][8][8][n]` table (the intended answer).
  - `surviveProbRec` — the exponential recursion (starting point / oracle).
  - `survivePaths` — **exact** surviving-path count via `BigInteger`; probability = `paths / 8^n`. No floating-point error, never underflows.

## Indexing pitfall

The table's last dimension has size `n` (valid indices `0..n-1`), so the value for `n`
moves lives at index **`n-1`**. Reading `subsol[xx-1][yy-1][zz-1][n]` throws
`ArrayIndexOutOfBoundsException` — an off-by-one in the read, not in the recurrence.

## Sample output

```
start(1,1,1) n=1  dp=0.1250000000  rec=0.1250000000  exact=1/8
start(1,1,1) n=2  dp=0.1250000000  rec=0.1250000000  exact=8/64
start(4,4,4) n=3  dp=1.0000000000  rec=1.0000000000  exact=512/512
start(1,1,1) n=5  dp=0.0305175781  rec=0.0305175781  exact=1000/32768
```

Sanity: corner + 1 move → only 1 of 8 diagonals stays on = **0.125**; center + 3 moves →
can't reach an edge in 3 steps = **1.0**.

## Follow-up: `double` precision

When does `double` break down as N grows and the probability shrinks?
The right model is IEEE 754: **relative** precision (~2⁻⁵³) is preserved regardless of
magnitude because the exponent floats; the real limits are **underflow to 0** (~10⁻³⁰⁸
normalized) and summation rounding (~N·2⁻⁵², negligible here). Dividing by 8 is an
*exact* exponent adjustment, not a mantissa loss. Exact alternative: the `BigInteger`
path count above. See wiki `theory/floating-point-representation`.
