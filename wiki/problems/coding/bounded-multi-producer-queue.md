# Bounded multi-producer queue

## Problem

Build a fixed-capacity FIFO shared by multiple writers and readers. Decide what an enqueue does when full and what a dequeue does when empty, including whether waiting can time out.

## Example

Two consumers wake for one available item. The first takes it, so the second must check the condition again before advancing the head. Ordering items and granting waiting threads fair access are separate requirements.

## Recorded approach

Use a circular buffer with head and tail pointers, a mutex and condition variables. The discussion included blocking, timeout and fairness options.

### Concurrency: Bounded MPMC Queue

- The candidate proposed a circular buffer with head/tail pointers, a mutex, and condition variables, with block, fail, or timeout options.
- For consumer fairness, suggested parking waiting consumers in an ordered queue to round-robin wakeups.

[Anonymized session notes](../../../sources/notes/coding/bounded-multi-producer-queue.md)

[Related practice implementation](../../../sources/code/lsm-kv-store/src/main/java/com/practice/concurrent/BoundedQueue.java)
