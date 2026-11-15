# Blocking topic queue

## Problem

Register a topic with a bounded buffer. Connect one producer and one consumer; preserve queued messages when the producer is faster and block the consumer when no item is available.

## Example

With capacity two, publishing A and B fills the queue. The third publish must wait until the subscriber removes A. The subscriber then receives B before the third message.

## Recorded approach

Coordinate producers and consumers on buffer availability.

[Source material](../../../sources/code/java-practice/practice/prep/concurrent/BlockingPubSub.java)
