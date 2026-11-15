# Vowel substrings with k consonants

## Problem

Count contiguous substrings that contain all five vowels and exactly k consonants. Repeated vowels can give several valid starts at one end position. Use a wide count for long strings.

## Example

For “aaeioub” and k=1, both “aaeioub” and “aeioub” qualify. Dropping the first a preserves coverage, but dropping both loses a required vowel.

## Recorded approach

Track vowel coverage along with the consonant budget to count valid starting positions for each end.

[Source material](../../../sources/code/exactly-k-subarray-counting/Solution.java)

## Study notes

The count of consonants and coverage of all vowels are separate constraints. A repeated leading vowel may be removed while preserving coverage, which widens the range of valid starts.

[Related analysis](../../reading-notes/exactly-k-subarray-counting.md)
