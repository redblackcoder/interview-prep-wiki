# Concurrent record access

## Problem

Allow several callers to read selected fields of one shared record while a writer updates fields exclusively. Readers must coordinate entry and exit as a group.

## Example

If two readers are active when a writer arrives, the first reader's exit cannot release exclusive access for the writer. The transition from one reader to zero is the one that matters.

## Recorded approach

The practice implementation counts active readers and coordinates the reader group with a writer semaphore.

[Source material](../../../sources/code/java-practice/practice/prep/concurrent/MultiReaderOneWriter.java)
