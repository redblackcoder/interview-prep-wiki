# Concurrent record access

Allow readers to access a record together while writes require exclusive access.

## Solution summary

The practice implementation counts active readers and coordinates the reader group with a writer semaphore.

[Full solution in interview-prep-raw](https://github.com/redblackcoder/interview-prep-raw/blob/master/code/java-practice/practice/prep/concurrent/MultiReaderOneWriter.java)
