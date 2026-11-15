# Streaming integer run codec

## Problem

Encode 32-bit integers incrementally as repeated values or literal groups of eight, then decode one value at a time. A repeat of at least eight extends as far as possible; the final group may be short.

## Example

Seven copies of 2 followed by 3 form one literal group of eight. Eight copies of 2 followed by 3 instead produce a repeat run and a final singleton. On a mode switch the triggering value must still appear in the output.

## Recorded approach

The encoder switches from a short repeat to a literal run when a different value arrives. The decoder advances a run index and an offset, yielding one value at a time.

### Problem Setup

- Task: encode and decode a stream of 32-bit integers using run-length encoding (RLE) and bit-packing (BP)
    - Focus on organized, structured code, not a hard algorithm; no real bit operations
- Rules: RLE needs at least 8 values and extends as long as possible; BP holds exactly 8 values
    - The last run may have fewer than 8; RLE is the default when either fits
- Template provides Run interface, RLE and BP classes, a streaming write/getData encoder, and a hasNext/next decoder
- Clarified that 1 followed by 2s and others becomes BP of 8 values, then RLE21 for the trailing value

### Solution Walkthrough

- Encoder greedily builds RLE, converts to BP when a short run breaks, and flushes full runs to a list
    - getData flushes any remaining partial RLE or BP before returning
- Fixed a bug where the triggering value was not added to BP after RLE-to-BP conversion
- Interviewer asked for on-the-fly decoding instead of decompressing everything, since that defeats compression
- Decoder tracks run index and inner index; fixed missing inner index reset, then tests passed

[Anonymized session notes](../../../sources/notes/coding/streaming-integer-run-codec.md)
