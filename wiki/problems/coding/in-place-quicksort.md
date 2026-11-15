# In-place quicksort

## Problem

Reorder an array of comparable values into ascending order by partitioning it in place. Account for ties and subarrays of size zero or one.

## Example

Partitioning [4,1,4,2] must preserve both copies of 4. Recursive calls must shrink even when several values equal the pivot. An empty or singleton partition needs no swaps.

## Recorded approach

Partition around a pivot and recursively sort each partition.

[Source material](../../../sources/code/java-practice/practice/prep/libs/QuickSort.java)
