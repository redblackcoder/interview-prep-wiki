# Streaming integer run codec

Encode and lazily decode integers in repeated-value runs or groups of eight literals.

## Solution summary

Start a candidate repeat run and switch to literal mode if a short run breaks.

[Full solution in interview-prep-raw](https://github.com/redblackcoder/interview-prep-raw/blob/master/code/worked-problems/streaming-integer-run-codec.md)
