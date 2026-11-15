# Multi-subscriber topic history

## Problem

Publish a sequence of messages to a topic and deliver retained messages independently to multiple subscribers. Bound the number of messages retained when a subscriber joins late or falls behind.

## Example

A fast subscriber reading A must not consume A on behalf of a slower subscriber. If the buffer holds only the last two messages and A, B and C have arrived, a late subscriber can begin with B and C.

## Recorded approach

Maintain bounded topic history and separate consumer progress.

[Source material](../../../sources/code/java-practice/practice/prep/concurrent/PubSub.java)
