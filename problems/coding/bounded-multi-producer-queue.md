# Bounded multi-producer queue

Coordinate many producers and consumers on a finite FIFO.

## Solution summary

Protect ring-buffer state with a lock and separate conditions for free space and available items.

[Full solution in interview-prep-raw](https://github.com/redblackcoder/interview-prep-raw/blob/master/code/worked-problems/bounded-multi-producer-queue.md)
