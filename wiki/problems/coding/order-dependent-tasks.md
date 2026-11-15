# Order dependent tasks

## Problem

Given tasks and prerequisite relationships, find an order in which every dependency has already completed when its dependent starts. Detect when no full order exists.

## Example

If build requires compile and test requires build, compile→build→test works. Making compile require test creates a cycle; returning a partial ordering would leave required tasks unscheduled.

## Recorded approach

Process the directed dependency graph in topological order.

[Source material](../../../sources/code/java-practice/practice/prep/problems/TopologySort.java)
