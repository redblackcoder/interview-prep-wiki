# Streaming integer run codec

Encode and lazily decode integers in repeated-value runs or groups of eight literals.

## Recorded approach

The encoder switches from a short repeat to a literal run when a different value arrives. The decoder advances a run index and an offset, yielding one value at a time.

[Anonymized session notes](https://github.com/redblackcoder/interview-prep-raw/blob/master/code/exercise-notes/streaming-integer-run-codec.md)
