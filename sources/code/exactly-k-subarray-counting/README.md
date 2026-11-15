# Exactly-k Subarray Counting (LC 992, LC 3306)

**Source:** self practice.

## Problems

- **LC 992 Subarrays with K Different Integers:** count subarrays with exactly `k` distinct numbers.
- **LC 3306 Count of Substrings Containing Every Vowel and K Consonants II:** count substrings that contain all five vowels and exactly `k` consonants. `n` can be 2 × 10^5, so the answer needs a `long`.

## Two approaches

Both are O(n). Both need a measure that can only grow as the window grows (distinct count, consonant count).

**A. Single pass over a range of valid starts (my approach).** For each end `r`, the starts that give exactly `k` form one range `[start, hi]`, so add `hi - start + 1`.
- `start` = one past the element that was dropped to keep the measure at `k` or below.
- `hi` = the largest start that still has enough:
  - 992: the smallest last-seen index among the `k` numbers in the window.
  - 3306: the smaller of (the oldest vowel last-seen index) and (the first consonant in the window).

**B. `atMost(k) - atMost(k - 1)`.** With at most `k`, the valid starts are always `[left, r]`, so it's a plain sliding window. In 3306 the "contains every vowel" condition caps the start at the oldest vowel last-seen, so the valid starts become `[left, minVowel]`.

## Bug in my first 992 version

In the branch where the map has `k + 1` numbers, I shrank the window and then did `count++`. After shrinking, the window has exactly `k` distinct again, and every start in `[start, min(lsi)]` is valid, so that branch should add `min(lsi) - start + 1` too.

The `[1,2,1,2,3]` trace didn't catch it, because both formulas gave 1 there. `[1,2,1,1,3]` with k = 2 returns 6; the correct answer is 7.

## Files

- `Solution.java` has:
  - `distinctOriginal`: the buggy 992 version.
  - `distinctRange`: the fixed 992 version. A `LinkedHashMap` in last-seen order makes the min lookup O(1).
  - `distinctAtMostDiff`: 992 with atMost.
  - `vowelsOriginal`: my 3306 version as written.
  - `vowelsRange`: 3306, simplified, using `long`.
  - `vowelsAtMostDiff`: 3306 with atMost.
  - brute-force checkers and a random test in `main`.

## Sample output

```
992 [1,2,1,1,3] k=2: original=6 range=7 atMost=7 brute=7
mismatches vs brute: 992=0 3306=0
3306 n=2e5 k=0: range=19999300006 atMost=19999300006
```
