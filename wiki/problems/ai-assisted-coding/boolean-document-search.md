# Boolean document search

## Problem

A client adds documents by ID and searches with terms, parentheses, AND and OR. Return IDs in relevance order; an exact word carries twice the weight of a partial match.

## Example

For “(red OR blue) AND boat”, a document containing only “red” fails the Boolean filter. Both “red boat” and “blue sailboat” qualify; the exact “boat” contributes more to the first document's score. Replacing a document requires updating its postings.

## Recorded approach

Index term frequencies by term and document ID. Evaluate AND/OR in a query tree and use weighted TF-IDF to rank matches.

The saved session describes a Java implementation. A term maps to postings containing document IDs and term frequencies. The parser was revised to represent parentheses and AND/OR as a tree. Set operations determine recall. Scores computed at the leaves can be added for the documents that survive those operations.

Whole-word matches received weight 1 and partial matches 0.5. The update follow-up compared removing and re-adding a document with applying its term-frequency deltas directly to the index.

[Anonymized session notes](../../../sources/notes/coding/boolean-document-search.md)
