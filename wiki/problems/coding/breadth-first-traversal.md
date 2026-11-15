# Breadth-first traversal

## Problem

From one source vertex, construct a traversal tree of reachable vertices and record each vertex's shortest distance in edges. Different paths may discover the same vertex.

## Example

For edges A→B, A→C, B→D and C→D, B and C have distance 1. D gets distance 2 and one parent, even though two routes reach it. Marking vertices at discovery prevents duplicate tree entries.

## Recorded approach

Visit vertices in frontier order and record the first edge that discovers each vertex.

[Source material](../../../sources/code/java-practice/practice/prep/problems/BFS.java)
