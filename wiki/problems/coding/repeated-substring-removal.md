# Repeated substring removal

## Problem

Repeatedly remove the first occurrence of a pattern until it no longer occurs. A deletion can create a new match across the gap, so the original occurrences alone do not determine the result.

## Example

Removing “ab” from “aabb” leaves “ab”. A second removal leaves the empty string. The saved code uses jump indices to skip deleted characters when checking newly adjacent text.

## Recorded approach

Recheck the remaining text as matches disappear.

[Source material](../../../sources/code/java-practice/practice/prep/problems/SubStringsRemoval.java)
