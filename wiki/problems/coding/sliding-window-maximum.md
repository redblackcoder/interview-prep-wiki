# Sliding window maximum

## Problem

Read a stream of values and report the maximum for each complete window of fixed width k. Once an item leaves the window, it cannot contribute to later maxima.

## Example

For [7,4,1,8] and k=3 the answers are 7 then 8. When 8 arrives it dominates the smaller, older candidates; storing indices also tells the deque when an old maximum expires.

## Recorded approach

Keep candidates in decreasing value order in a deque. Drop dominated values from the back and expired indices from the front. The front holds the maximum.

[Source material](../../../sources/code/java-practice/practice/prep/libs/StreamMax.java)
