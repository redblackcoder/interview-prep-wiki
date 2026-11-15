# Sliding window maximum

Find a maximum as a fixed-width window moves through a stream.

## Solution summary

Keep candidates in decreasing value order in a deque. Drop dominated values from the back and expired indices from the front. The front holds the maximum.

[Full solution in interview-prep-raw](https://github.com/redblackcoder/interview-prep-raw/blob/master/code/java-practice/practice/prep/libs/StreamMax.java)
