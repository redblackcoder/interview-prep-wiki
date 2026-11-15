# Keyed minimum heap

## Problem

Keep entries in minimum-priority order while allowing an existing key's priority to be updated. A key identifies one entry even after swaps change its position in the underlying array.

## Example

With A at priority 8 and B at 3, lowering A to 1 must put A at the root. Looking up A's array position by key avoids scanning the entire heap; the lookup must follow any swaps.

## Recorded approach

Track key positions alongside the heap and restore heap order after updates.

[Source material](../../../sources/code/java-practice/practice/prep/libs/MinHeap.java)
