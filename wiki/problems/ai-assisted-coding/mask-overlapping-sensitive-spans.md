# Mask overlapping sensitive spans

## Problem

Each detector takes an original line and returns zero or more half-open character ranges. Combine the ranges across detectors and return the processed lines in input order.

## Example

On “abcdef”, spans [1,4) and [3,5) both include the fourth character. The combined coverage is “bcde”. The saved processor gets as far as collecting both ranges; its rendering method returns the unchanged line.

## Recorded approach

The processor collects half-open spans from each detector for each input line. Its apply method still returns the original line and contains the original TODO.

[Saved Java implementation (partial)](../../../sources/code/java-practice/practice/aiassisted/pii/Processor.java)
