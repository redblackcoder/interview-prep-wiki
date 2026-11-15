# Block-aligned range cache

Serve arbitrary byte ranges using a local cache of fixed-size remote blocks.

## Solution summary

Split a read at block boundaries and coalesce concurrent fetches of the same block. The original code was a sketch.

[Full solution in interview-prep-raw](https://github.com/redblackcoder/interview-prep-raw/blob/master/code/worked-problems/block-aligned-range-cache.md)
