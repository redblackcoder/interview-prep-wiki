# Word membership trie

## Problem

Insert words and test whether a complete word was inserted. Searching for a shared prefix must distinguish an intermediate path from a stored word.

## Example

After inserting “card” and “care”, the path c→a→r exists. “car” becomes a member only when it too is inserted and its ending recorded.

## Recorded approach

Share prefix paths and mark word endings so a prefix alone does not count.

[Source material](../../../sources/code/java-practice/practice/prep/libs/Trie.java)
