# Hierarchical location registry

## Problem

Register hierarchical locations and retrieve registrations that overlap a query by ancestry. Comparisons must respect path segments and handle queries both above and below a registered path.

## Example

With registered leaves /data/a and /data/b, querying /data returns both. A query under /data/a refers back to that registered ancestor; /database shares characters but no matching first segment.

## Recorded approach

Index path segments in a trie. An ancestor query enumerates leaves below the prefix and a descendant query resolves to the registered leaf.

[Source material](../../../sources/code/java-practice/practice/prep/problems/LocationRegistry.java)
