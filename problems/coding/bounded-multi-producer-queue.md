# Bounded multi-producer queue

Coordinate many producers and consumers on a finite FIFO.

## Recorded approach

Use a circular buffer with head and tail pointers, a mutex and condition variables. The discussion included blocking, timeout and fairness options.

[Anonymized session notes](https://github.com/redblackcoder/interview-prep-raw/blob/master/code/exercise-notes/bounded-multi-producer-queue.md)
