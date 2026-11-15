# Merge sorted arrays in place

## Problem

The first sorted array has m occupied positions and trailing capacity for all n values in a second sorted array. Produce the sorted union in the first array and preserve duplicates.

## Example

For [2,4,_,_] and [1,3], the last destination position belongs to 4. Filling that position first leaves the unread prefix intact. A forward insertion instead has to shift existing values.

## Recorded approach

The proposed forward insertion shifted batches. Reverse filling was discussed as a linear alternative, and its implementation remained unfinished.

### Coding: In-Place Merge of Two Sorted Arrays

- Problem: merge second sorted array into first (which has enough trailing free space) in place.
- The candidate proposed forward insertion with batch shifts sized to the gap between adjacent list-one elements.
- Worst-case complexity is O(M\*N) due to repeated shifting; considered filling from the right for O(M+N).
- Did not fully land the reverse-fill approach within time.

[Anonymized session notes](../../../sources/notes/coding/merge-sorted-arrays-in-place.md)
