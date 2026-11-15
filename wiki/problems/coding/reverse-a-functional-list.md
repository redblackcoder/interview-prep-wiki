# Reverse a functional list

## Problem

Reverse an immutable list using an accumulator. Prepending to a linked list is cheap, while repeatedly appending at the tail would traverse the partial result.

## Example

Reading [1,2,3] left to right builds the accumulator [], [1], [2,1], then [3,2,1]. The input list can still be used afterward.

## Recorded approach

Fold the input into an accumulator by prepending each element.

[Source material](../../../sources/code/elm-list-reversal/Main.elm)
