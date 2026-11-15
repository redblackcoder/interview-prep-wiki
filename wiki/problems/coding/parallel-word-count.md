# Parallel word count

## Problem

Read a directory of text files and count occurrences of each word across the files using multiple threads. Return one map after all workers finish.

## Example

If one file contains “red blue” and another “red red”, the final count for red is 3. Two workers incrementing red concurrently must not overwrite one another's update.

## Recorded approach

Distribute file processing among workers and combine counts in a concurrent map.

[Source material](../../../sources/code/java-practice/practice/prep/concurrent/ParallelWordCount.java)
