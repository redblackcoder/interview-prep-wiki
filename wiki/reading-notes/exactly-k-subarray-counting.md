---
source: ../../sources/code/exactly-k-subarray-counting
type: code
date_extracted: 2026-10
topic: Technical interview prep for senior roles
---

# Exactly-k Subarray Counting: LC 992 and LC 3306

## Key Ideas
- To count subarrays with **exactly k** of something, fix the right end `r` and count its valid starts. Those starts always form one contiguous range `[lo, hi]`, as long as the measure can only grow when the window grows.
- **Approach A (single pass):** track `lo` (shrink when the measure goes above k) and `hi` (the largest start that still has at least k), and add `hi - lo + 1`. How you find `hi` depends on the measure:
  - distinct numbers (992): the smallest last-seen index;
  - every vowel plus k consonants (3306): the smaller of the oldest vowel last-seen index and the first consonant in the window;
  - k odd numbers or binary sum = k: the index of the k-th most recent qualifying element.
- **Approach B (`atMost(k) - atMost(k - 1)`):** with at most k, the valid starts are `[left, r]`, so it's a plain sliding window that never needs `hi`. It's the same every time, which makes it the safer choice in an interview.
- **When both fail:** if the measure can go down as the window grows (sums with negative numbers, LC 560), neither works. Use prefix sums with a hash map instead.
- **A condition that needs enough** (3306's "contains every vowel") limits how far right a start can be. Even inside atMost, the valid starts become `[left, minVowel]`.

## My Understanding
- The last-seen-index approach is the range method. For each end, the starts from `start` up to the smallest last-seen index all have exactly k distinct.
- After shrinking, the window is guaranteed to have exactly k distinct values again. The map can only reach k + 1, because each step adds one number, and one removal brings it back to k. So the shrink branch has to add the full range, `min(lsi) - start + 1`, not 1 — a `count++` there is the bug to avoid.
- A trace on `[1,2,1,2,3]` doesn't catch that bug, because the range happens to be 1 there. `[1,2,1,1,3]` with k = 2 exposes it: the buggy code gives 6, the answer is 7.
- In 3306 the upper bound has to include the first consonant in the window, because the substring has to contain all k consonants. Getting that right is the hard part of approach A.
- atMost is more general and simpler to get right. Approach A needs a fresh way to find `hi` for each new measure.

## Open Questions
- Drill `hi` for other measures (1248 nice subarrays, 930 binary sum) until it's as fast as writing atMost.
- 3306 at full size: `int` overflows, because the answer can reach about n^2 / 2. Always ask whether the count needs a `long`.

## Connections
- Relates to: [exactly k window counting](../coding-patterns/exactly-k-window-counting.md): the pattern page built from this extract
- Relates to: [concurrent lru cache](../coding-patterns/concurrent-lru-cache.md): the same `LinkedHashMap` trick (remove and re-insert to keep last-access order) makes `min(lsi)` O(1) in 992
- Relates to: [sweep line resource scheduling](../coding-patterns/sweep-line-resource-scheduling.md): both come down to "for each position, count what's valid", and both are easy to get wrong with a test that checks the code against itself.

## Key Quotes / Annotations
- The bug: `else { start = min(lsi); lsi.remove(nums[start++]); count++; }`. It should be `count += min(lsi) - start + 1`.
- Verified: 20,000 random arrays and strings against a brute-force count, with no mismatches for the fixed 992, the original 3306, the simplified 3306, or both atMost versions.
