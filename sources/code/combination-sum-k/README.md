# Combination Sum from {1..9}

Coding interview problem. HackerRank-style, "array sum" flavored.

## Problem
Given the fixed set of integers `{1, 2, 3, 4, 5, 6, 7, 8, 9}`, return **all combinations** whose sum equals a target `n`.

- Within one combination a number is **not repeated**.
- The same number **may** appear across different combinations.
- Two variants:
  1. any number of integers sum to `n`;
  2. exactly `k` integers sum to `n` (second input `k` = count of integers in a combination).

Example: `n = 9, k = 3` → `[1,2,6] [1,3,5] [2,3,4]`.

## Approach
Backtracking over the include/exclude decision tree on candidates `1..9`. Carry a running `currSum` so the partial sum is never recomputed.

Key pruning that makes the `k` variant tidy — the base-case ordering:
```
if (size == k && sum == n) { record; return; }   // exact hit
if (next == 10 || size == k) return;              // out of candidates, or k reached w/ wrong sum
```
Reaching size `k` always stops the branch: either it's recorded (right sum) or abandoned (wrong sum). No overshoot. The no-`k` variant instead prunes on `currSum > n`.

## Complexity
Worth spelling out both framings:

- **As literally stated (fixed set {1..9}):** bounded input → bounded work → **O(1)**. At most 2^9 = 512 subsets, `k <= 9`, `n <= 45`. Technically correct, but usually not the framing being asked for.
- **Generalized to a set of size N (1..N):** exponential. The include/exclude tree visits up to O(2^N) nodes; producing each valid size-`k` combination costs O(k) to copy. So output cost is **O(k * C(N, k))** inside an O(2^N) worst-case traversal.
- **Trap:** "linear in k" is only the cost to copy *one* result (`new ArrayList<>(curr)` + print), not the algorithm's total. Presenting it as the answer downgrades a correct answer.

Tip: a repeated complexity question is usually asking for the generalized framing; give it explicitly instead of re-defending "it's constant."

## Run
```
javac Solution.java
java Solution      # prints combinations for n=9, k=3
```
Requires JDK 8+. `main` is a small demo, not a test suite.
