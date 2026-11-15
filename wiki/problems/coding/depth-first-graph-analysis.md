# Depth-first graph analysis

## Problem

Traverse a directed graph, record discovery and finishing order, and report when a directed cycle makes a topological order impossible.

## Example

With A→B→C→A, the last edge goes back to a vertex still on the recursion path. An edge to an already completed vertex would not establish the same cycle. Finishing times only yield an ordering when no such cycle exists.

## Recorded approach

Track the active DFS path to identify back edges and use finishing order for a topological ordering of a DAG.

[Source material](../../../sources/code/java-practice/practice/prep/problems/DFS.java)
