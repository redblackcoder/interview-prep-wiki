# Concurrent linked list

## Problem

Support indexed reads, insertion and removal while several threads traverse the same linked list. Each operation needs a stable predecessor at the point it reads or changes a link.

## Example

To delete the second element of A→B→C, the operation must still own a valid predecessor when it replaces A's next link with C. Hand-over-hand locking transfers protection as traversal advances.

## Recorded approach

Coordinate access to nodes during traversal and mutation.

[Source material](../../../sources/code/java-practice/practice/prep/concurrent/linkedlist/LinkedList.java)
