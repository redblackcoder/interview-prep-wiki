# Concurrent LRU cache

Maintain recency and bounded capacity under concurrent reads and writes.

## Solution summary

Compare a locked recency list with sharding and approximate clock eviction to reduce read contention.

[Full solution in interview-prep-raw](https://github.com/redblackcoder/interview-prep-raw/blob/master/code/concurrent-lru-cache/README.md)
