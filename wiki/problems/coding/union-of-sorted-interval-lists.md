# Union of sorted interval lists

## Problem

Combine two lists of closed intervals, each already sorted by start. Return a sorted, coalesced union, including overlaps that occur near the end of either list.

## Example

For [1,3], [8,9] in one list and [3,5] in the other, the result is [1,5], [8,9]. Closed intervals sharing 3 merge, and the last pending interval must still be emitted when both streams end.

## Recorded approach

Merge their start-ordered streams and hold one pending interval until the next start exceeds its end.

[Source material](../../../sources/code/java-practice/practice/prep/problems/MergeIntervals.java)
