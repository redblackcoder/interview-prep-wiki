# Weight-bounded LRU cache

Bound a cache by total entry weight while preserving recency.

## Solution summary

Index entries by key and evict the least recently used entries until the weight fits.

[Full solution in interview-prep-raw](https://github.com/redblackcoder/interview-prep-raw/blob/master/code/java-practice/practice/prep/problems/WLRUCache.java)
