# Weight-bounded LRU cache

## Problem

Maintain a key-value cache with a total weight budget. Reads affect recency, and replacing an existing value changes its contribution to total weight. Evict old entries until the new item fits.

## Example

With budget 5, entries A of weight 2 and B of weight 3 fill the cache. If B was just used and C of weight 2 arrives, evicting A makes precisely enough room. An item heavier than the entire budget cannot fit.

## Recorded approach

Index entries by key and evict the least recently used entries until the weight fits.

[Source material](../../../sources/code/java-practice/practice/prep/problems/WLRUCache.java)
