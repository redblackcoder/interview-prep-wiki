# Maximum stock profit

## Problem

Choose at most one day to buy and a later day to sell. Return the greatest possible gain; skipping the trade is allowed.

## Example

For [7,1,5,3], buying at 1 and selling later at 5 earns 4. Selling at the initial 7 is impossible after buying at 1. A falling sequence yields zero.

## Recorded approach

Scan backward and track the best later selling price.

The recorded one-trade solution scans backward through prices, keeps the maximum future sale price and compares the profit from buying at the current price. A forward pass maintaining a minimum purchase price came up during discussion.

[Anonymized session notes](../../../sources/notes/coding/maximum-stock-profit.md)
