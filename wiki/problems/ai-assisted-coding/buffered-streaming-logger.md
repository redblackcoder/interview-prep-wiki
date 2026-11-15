# Buffered streaming logger

## Problem

Many threads append messages to one bounded buffer. The sink accepts batches, can fail transiently, and must receive a final flush when the logger closes.

## Example

A single message in a quiet period must eventually reach the sink even if no second message arrives. When the buffer fills, producers wait while the flusher retries; once a batch succeeds, its slots become available again.

## Recorded approach

Wake a background flusher on capacity or timeout, bound ingestion, and retry transient sink failures.

[Source material](../../../sources/code/java-practice/practice/aiassisted/streaming/Logger.java)
