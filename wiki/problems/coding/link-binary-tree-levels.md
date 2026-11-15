# Link binary tree levels

## Problem

Set every node's next pointer to its immediate right neighbor at the same depth and set the last pointer on each level to null. Trees may be sparse and existing next pointers may be stale.

## Example

If the root's left child has only a right child and the root's right child has only a left child, those two grandchildren still become neighbors. Walking the completed parent level finds both without assuming a perfect tree.

## Recorded approach

Walk the current level through its newly established links while appending its children to the next level.

[Source material](../../../sources/code/java-practice/practice/prep/problems/BinrayLevelConnection.java)
