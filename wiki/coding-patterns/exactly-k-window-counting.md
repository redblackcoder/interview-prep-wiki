# Exactly-k Window Counting

These problems ask how many subarrays or substrings have **exactly k** of something: k distinct numbers, k odd numbers, a sum of k, or k consonants plus every vowel. A plain sliding window doesn't fit directly. For a fixed right end, the valid starts aren't "everything from `left` to here"; they're a **range in the middle**. There are two ways to handle that:
- **A.** Track both ends of that range in one pass.
- **B.** Count `atMost(k) - atMost(k - 1)`, where each atMost is a plain sliding window.

## When it applies

Both approaches need a **measure that can only grow as the window grows**, so shrinking the window can never increase it.

| Measure | Monotone? | Example |
|---|---|---|
| distinct count | yes | LC 992 |
| number of odd numbers / sum of 0s and 1s | yes | LC 1248, LC 930 |
| sum of non-negative numbers | yes | LC 930, LC 713 (a product works too) |
| the window's maximum | yes | LC 795 |
| consonant count (+ "contains every vowel") | yes | LC 3306 |
| **sum with negative numbers** | **no** | LC 560: use prefix sums with a hash map |

Also skip it for "longest or shortest window" questions. A single sliding window already answers those.

## The key fact

Fix the right end `r`. Because the measure is monotone, the starts that give **exactly k** form one contiguous range `[lo, hi]`, and both ends only move right as `r` grows.
- `lo` is the smallest start whose window has **at most k**.
- `hi` is the largest start whose window has **at least k**.

Each step adds `hi - lo + 1`. Both approaches compute this sum; they differ only in how they handle `hi`.

## Approach A: single pass, range of valid starts

Keep `start` (that's `lo`) by shrinking when the measure goes above k. Find `hi` using something specific to the measure:

| Measure | `hi` = | State |
|---|---|---|
| k distinct (992) | the smallest last-seen index among the k numbers in the window | `LinkedHashMap` kept in last-seen order (remove and re-insert), so the first entry is the min in O(1) |
| every vowel + k consonants (3306) | the smaller of (the oldest vowel last-seen index) and (the first consonant in the window) | 5 vowel last-seen indices plus a deque of consonant positions |
| k odd numbers (1248) / binary sum k (930) | the index of the k-th most recent odd number or 1 | a deque of positions |
| anything | a second left pointer with its own counter (the "three pointers" form) | a second counter |

```java
// LC 992 core loop
lsi.remove(x); lsi.put(x, end);                  // keep last-seen order
if (lsi.size() > k) {                            // at most k+1: one new number per step
    var oldest = lsi.entrySet().iterator().next();
    start = oldest.getValue() + 1;
    lsi.remove(oldest.getKey());
}                                                // now exactly k again
if (lsi.size() == k) count += firstValue(lsi) - start + 1;
```

**Trap (actual bug):** after shrinking, `count++` assumes only one valid start. The window is back to exactly k, and every start up to `hi` is valid, so add the full range. `[1,2,1,2,3]` hides the bug because the range is 1 there; `[1,2,1,1,3]`, k = 2 exposes it (6 instead of 7).

**Trap (3306):** `hi` has to include the first consonant in the window, because the substring has to contain all k consonants. A stale or never-seen vowel index (−1) is below `start`, so one `hi >= start` check covers "all vowels present"; you don't need a reset loop.

## Approach B: atMost(k) − atMost(k − 1)

With **at most** k, valid starts are `[left, r]`: any shorter window has at most k too. So it's a standard sliding window, and you never need `hi`.

```java
long exactly(int[] a, int k) { return atMost(a, k) - atMost(a, k - 1); }

long atMost(int[] a, int k) {
    if (k < 0) return 0;                 // guard: atMost(-1) when k = 0
    long count = 0;
    int left = 0;
    for (int right = 0; right < a.length; right++) {
        add(a[right]);
        while (measure() > k) remove(a[left++]);   // while, not if: a sum can overshoot by a lot
        count += right - left + 1;
    }
    return count;
}
```

Dry run, 992 `[1,2,1,2,3]`, k = 2: `atMost(2)` = 1+2+3+4+2 = 12 and `atMost(1)` = 1+1+1+1+1 = 5, so the answer is **7**.

**Variant: a condition that needs enough.** In 3306, atMost runs over consonants, but "contains every vowel" caps the start at the oldest vowel last-seen index. So the valid starts become `[left, minVowel]`: `if (minVowel >= left) count += minVowel - left + 1`. To spot this case: one condition is broken by too much (it sets `left`) and another by too little (it sets `hi`).

**Variant: at least k** (1358, 2962): either `total - atMost(k - 1)`, or shrink while the window *still* has at least k, then `count += left`, since every start before `left` was valid.

## Picking between them

| | A: range method | B: atMost |
|---|---|---|
| Passes | 1 | 2 |
| What you work out | how to find `hi` for this measure (new each time) | nothing new: same template every time |
| Failure mode | getting `hi` wrong, or counting 1 instead of the range | forgetting the `k < 0` guard |
| Use in interview | as a one-pass follow-up | **by default** |

**30-second interview line:** "Exactly-k is awkward because the valid starts form a range with two moving boundaries. At-most-k is monotone, so the valid starts are always `[left, r]`, a standard sliding window. I'll compute atMost(k) minus atMost(k − 1): two O(n) passes, O(1) or O(distinct) space. Checks: is the measure monotone, k = 0, and the count needs a `long`, since it can reach about n^2 / 2."

## Practice ladder
| # | Problem | What it adds |
|---|---|---|
| 713 | Subarray Product Less Than K | atMost directly, without the subtraction |
| 930 | Binary Subarrays With Sum | sum as the measure |
| 1248 | Count Number of Nice Subarrays | same as 930, counting odd numbers |
| 992 | Subarrays with K Different Integers | frequency map as the window state |
| 795 | Number of Subarrays with Bounded Maximum | the max: atMost(R) − atMost(L − 1) |
| 1358 | Number of Substrings Containing All Three Characters | the `count += left` form of at least |
| 2962 | Count Subarrays Where Max Element Appears at Least K Times | at least, again |
| 3306 | Count of Substrings Containing Every Vowel and K Consonants II | combines an at-most and an at-least condition, and needs a `long` |
| 560 | Subarray Sum Equals K | **the case where it fails:** explain why, then use prefix sums |

Before coding each one, say what the measure is and why it only grows as the window grows. Work out the expected count by hand from the definition before running anything.

## Connections
- [[coding-patterns/concurrent-lru-cache]]: the same `LinkedHashMap` remove-and-reinsert ordering that gives LRU order also gives the O(1) "smallest last-seen index" in approach A
- [[coding-patterns/sweep-line-resource-scheduling]]: a sibling way to count by position: for each end (here) or each moment (there), count what's valid using endpoints that only move one way
- [[algorithms/knapsack-variants]]: the contrast: when the measure isn't monotone, a window doesn't work, and you fall back to prefix sums or a DP over the state

## Sources
- [[sources/code/exactly-k-subarray-counting]]: my 992 last-seen solution (with the `count++` bug), my 3306 solution, both compared with atMost
- Runnable code with brute-force checks: [exactly-k-subarray-counting/](https://github.com/redblackcoder/interview-prep-raw/blob/master/code/exactly-k-subarray-counting/)
