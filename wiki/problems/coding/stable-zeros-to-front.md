# Stable zeros to front

## Problem

Rearrange an array in place so all zeros precede all nonzeros while preserving the relative order of the nonzeros. Use one linear scan plus filling the remaining prefix.

## Example

[2,0,3,0,1] becomes [0,0,2,3,1]. Writing nonzeros from the right preserves their order without destroying the entries still waiting to be read.

## Recorded approach

The Java implementation scanned from the end with two pointers, moving zeros to the front while preserving the order of other values.

### Coding Exercise

- Task: move all zeros to the array start, preserving order of others; the candidate solved it in Java with a two-pointer O(n) pass from the end.
- Tested normal, no-zero, and empty arrays; removed a redundant while loop when the candidate asked about optimization.

[Anonymized session notes](../../../sources/notes/coding/stable-zeros-to-front.md)
