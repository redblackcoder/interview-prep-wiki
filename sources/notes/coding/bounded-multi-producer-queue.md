# Bounded multi-producer queue

Anonymized session notes.

## Concurrency: Bounded MPMC Queue

- The candidate proposed a circular buffer with head/tail pointers, a mutex, and condition variables, with block, fail, or timeout options.
- For consumer fairness, suggested parking waiting consumers in an ordered queue to round-robin wakeups.
