# Local maxima in a matrix

## Problem

For every three-by-three window of an n×n grid, output its largest value at the corresponding position in an (n−2)×(n−2) grid. Windows overlap in both dimensions.

## Example

A four-by-four grid produces four results. A large value in one of the middle four cells belongs to all four windows; keeping just the maximum of each whole row would lose the column boundaries.

## Recorded approach

Compute row-window maxima and combine vertically. The implementation compares this approach with a direct scan.

[Source material](../../../sources/code/java-practice/practice/prep/problems/LocalMaxMatrix.java)
