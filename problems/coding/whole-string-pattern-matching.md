# Whole-string pattern matching

Match a string against literals, dot and the zero-or-more star operator.

## Solution summary

Memoize suffix pairs. At a starred atom either skip it or consume one matching character and stay at that atom.

[Full solution in interview-prep-raw](https://github.com/redblackcoder/interview-prep-raw/blob/master/code/worked-problems/whole-string-pattern-matching.md)
