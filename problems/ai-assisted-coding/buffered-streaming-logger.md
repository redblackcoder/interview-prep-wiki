# Buffered streaming logger

Accept concurrent log messages and flush batches to a fallible sink.

## Solution summary

Wake a background flusher on capacity or timeout, bound ingestion, and retry transient sink failures.

[Full solution in interview-prep-raw](https://github.com/redblackcoder/interview-prep-raw/blob/master/code/java-practice/practice/aiassisted/streaming/Logger.java)
