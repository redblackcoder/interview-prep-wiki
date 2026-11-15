# Streaming integer run codec

Anonymized session notes.

## Problem Setup

- Task: encode and decode a stream of 32-bit integers using run-length encoding (RLE) and bit-packing (BP)
    - Focus on organized, structured code, not a hard algorithm; no real bit operations
- Rules: RLE needs at least 8 values and extends as long as possible; BP holds exactly 8 values
    - The last run may have fewer than 8; RLE is the default when either fits
- Template provides Run interface, RLE and BP classes, a streaming write/getData encoder, and a hasNext/next decoder
- Clarified that 1 followed by 2s and others becomes BP of 8 values, then RLE21 for the trailing value

## Solution Walkthrough

- Encoder greedily builds RLE, converts to BP when a short run breaks, and flushes full runs to a list
    - getData flushes any remaining partial RLE or BP before returning
- Fixed a bug where the triggering value was not added to BP after RLE-to-BP conversion
- Interviewer asked for on-the-fly decoding instead of decompressing everything, since that defeats compression
- Decoder tracks run index and inner index; fixed missing inner index reset, then tests passed
