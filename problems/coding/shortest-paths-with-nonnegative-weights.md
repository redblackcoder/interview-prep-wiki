# Shortest paths with nonnegative weights

Find shortest paths from a source in a weighted graph.

## Solution summary

Order the priority queue by total tentative distance. Skip stale entries and relax outgoing edges from the best known distance.

[Full solution in interview-prep-raw](https://github.com/redblackcoder/interview-prep-raw/blob/master/docs/dijkstra-edge-weight-heap-counterexample.html)
