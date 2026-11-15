# Boolean document search

Anonymized session notes.

The saved session describes a Java implementation. A term maps to postings containing document IDs and term frequencies. The parser was revised to represent parentheses and AND/OR as a tree. Set operations determine recall. Scores computed at the leaves can be added for the documents that survive those operations.

Whole-word matches received weight 1 and partial matches 0.5. The update follow-up compared removing and re-adding a document with applying its term-frequency deltas directly to the index.
