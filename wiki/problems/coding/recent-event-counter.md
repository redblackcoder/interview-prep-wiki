# Recent event counter

## Problem

Record event timestamps and return how many fall in the most recent five minutes. The deque approach assumes timestamps arrive in order; equal timestamps represent separate events.

## Example

At query time 300,000 ms, an event at 0 is exactly on the cutoff and is counted by the saved code. One millisecond later it expires. Removing entries only when they are strictly older determines this behavior.

## Recorded approach

Append timestamped events to a deque and remove entries strictly older than the window start. An event exactly at the cutoff still counts.

[Saved Java excerpt](../../../sources/code/Count%20Events.md)

[Related practice implementation](../../../sources/code/lsm-kv-store/src/main/java/com/practice/problems/HitCounter.java)
