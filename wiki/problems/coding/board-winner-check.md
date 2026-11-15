# Board winner check

## Problem

Given a square board, determine whether a player's mark fills an entire row, column or either main diagonal. Consider how the cost changes if the board reports each new move.

## Example

Three marks at (0,0), (1,1) and (2,2) win on a three-by-three board. A snapshot scan needs to check the lines, whereas knowing the most recent move restricts which lines could have changed.

## Recorded approach

The Java solution checked rows, columns and both diagonals. The follow-up proposed updating per-player counters after each move.

### Coding: Tic-Tac-Toe Winner Check

- The candidate implemented an N x N board evaluator in Java checking rows, columns, and both diagonals: O(N^2) time.
- Discussed binary-search-per-row optimization giving O(N log N), then dismissed hashmap approaches for the static-board case.
- Proposed incremental approach: maintain row/column/diagonal counters per player, updated per move, giving O(1) win check per move.
- Outlined class structure: Board with update method, Game holding board and state, Driver class handling turns via CLI/UI hooks.

[Anonymized session notes](../../../sources/notes/coding/board-winner-check.md)
