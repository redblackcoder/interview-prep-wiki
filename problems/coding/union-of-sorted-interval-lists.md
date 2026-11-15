# Union of sorted interval lists

Return the union of two ordered lists of closed intervals.

## Solution summary

Merge their start-ordered streams and hold one pending interval until the next start exceeds its end.

[Full solution in interview-prep-raw](https://github.com/redblackcoder/interview-prep-raw/blob/master/code/java-practice/practice/prep/problems/MergeIntervals.java)
