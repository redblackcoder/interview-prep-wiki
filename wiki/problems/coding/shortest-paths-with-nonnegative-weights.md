# Shortest paths with nonnegative weights

## Problem

Find minimum total path costs from a source in a graph of nonnegative edge weights. Decide what priority belongs in the heap when relaxing an edge.

## Example

With S→A costing 4, S→B costing 2 and B→A costing 3, the path through B costs 5. Ranking the last edge of that route by 3 could make it look cheaper than the direct path of cost 4.

## Recorded approach

Order the priority queue by total tentative distance. Skip stale entries and relax outgoing edges from the best known distance.

[Source material](../../../sources/references/dijkstra-edge-weight-heap-counterexample.html)
