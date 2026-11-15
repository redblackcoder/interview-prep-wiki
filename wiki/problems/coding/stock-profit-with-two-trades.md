# Stock profit with two trades

## Problem

Choose at most two non-overlapping buy/sell pairs to maximize profit on one price sequence. The second buy must be possible after the first sale.

## Example

For [1,5,2,7], the best single trade earns 6, but two trades earn 4+5=9. A split after 5 needs the best profit on both sides, including earlier prefix trades whose selling day is before the split.

## Recorded approach

The follow-up split the prices into prefix and suffix ranges. The implementation ended with a known bug.

The recorded approach combined a left-side profit with a right-side profit at a split. The suffix results needed to be precomputed and the prefix scan needed to carry its prior best. Those fixes were identified but the session ended before a working implementation was recorded.

[Anonymized session notes (partial)](../../../sources/notes/coding/stock-profit-with-two-trades.md)
