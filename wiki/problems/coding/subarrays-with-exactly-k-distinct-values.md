# Subarrays with exactly k distinct values

## Problem

Count all contiguous subarrays containing exactly k distinct integers. Multiple valid starting positions can share the same end; each contributes a separate subarray.

## Example

For [1,2,1] and k=2, [1,2], [2,1] and [1,2,1] qualify. Incrementing only once per right endpoint misses one of the two qualifying slices ending at the last 1.

## Recorded approach

Subtract the number with at most k−1 distinct values from the number with at most k.

[Source material](../../../sources/code/exactly-k-subarray-counting/Solution.java)

## Study notes

For each right endpoint the valid starts form a range. Maintain its two boundaries or compute two at-most totals; both approaches avoid enumerating every slice.

[Related analysis](../../reading-notes/exactly-k-subarray-counting.md)
