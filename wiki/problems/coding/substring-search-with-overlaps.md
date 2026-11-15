# Substring search with overlaps

## Problem

Preprocess a pattern, then find every starting index where it occurs in a text. Occurrences may share characters; a match must not force scanning to restart after its end.

## Example

Searching “aaaa” for “aa” returns indices 0, 1 and 2. After each match, the final “a” is also a possible beginning of the next match.

## Recorded approach

Precompute prefix fallback lengths so a mismatch can reuse the matched suffix.

[Source material](../../../sources/code/java-practice/practice/prep/problems/SubstringMatchKMP.java)
