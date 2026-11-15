# Hierarchical location registry

Register paths and find registered locations that overlap a requested ancestor or descendant.

## Solution summary

Index path segments in a trie. An ancestor query enumerates leaves below the prefix and a descendant query resolves to the registered leaf.

[Full solution in interview-prep-raw](https://github.com/redblackcoder/interview-prep-raw/blob/master/code/java-practice/practice/prep/problems/LocationRegistry.java)
