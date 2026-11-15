# Mask overlapping sensitive spans

Mask portions of each line identified by multiple detectors.

## Recorded approach

The processor collects half-open spans from each detector for each input line. Its apply method still returns the original line and contains the original TODO.

[Saved Java implementation (partial)](https://github.com/redblackcoder/interview-prep-raw/blob/master/code/java-practice/practice/aiassisted/pii/Processor.java)
