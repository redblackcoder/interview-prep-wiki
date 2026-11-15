# Whole-string pattern matching

## Problem

Match the entire input against literals, dot and star. Dot consumes one character; star applies to the preceding atom and allows zero or more repetitions.

## Example

For “aab” and “c*a*b”, skip c*, consume both a characters through a*, then match b. Matching only a prefix of the input would not satisfy the whole-string requirement.

## Recorded approach

The notes identify the two branches for a star: skip the atom or consume one matching character while staying at the atom.

[Recorded practice notes](../../../sources/notes/coding/whole-string-pattern-matching.md)
