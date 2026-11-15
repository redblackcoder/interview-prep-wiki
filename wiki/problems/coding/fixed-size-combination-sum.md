# Fixed-size combination sum

## Problem

Choose exactly k distinct numbers from 1 through 9 whose sum equals a target. Each number can be used once; changing the order of a selection does not produce a new answer.

## Example

For k=3 and target 7, [1,2,4] qualifies. [1,1,5] repeats a digit and [3,4] uses too few. Extending candidates only in increasing order avoids returning [4,2,1] a second time.

## Recorded approach

Extend combinations in increasing order and backtrack when either the count or remaining sum rules out a solution.

[Source material](../../../sources/code/combination-sum-k/Solution.java)

## Study notes

Prune a branch when its count reaches k. Check for a completed solution before pruning so a valid last digit is retained.

[Related analysis](../../reading-notes/combination-sum-and-reservation-interview.md)
