# Search autocomplete

Return the ten highest-ranked keyword suggestions while a user types.

## Recorded approach

The diagram compares precomputed top-ten results per prefix with sharded tries. A ranker and retriever sit behind the autocomplete service, and a query corpus feeds the indexer.

[Original architecture sketch (anonymized crop)](https://github.com/redblackcoder/interview-prep-raw/blob/master/docs/designs/search-autocomplete.md)
