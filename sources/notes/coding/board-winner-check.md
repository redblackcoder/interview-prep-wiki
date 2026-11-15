# Board winner check

Anonymized session notes.

## Coding: Tic-Tac-Toe Winner Check

- The candidate implemented an N x N board evaluator in Java checking rows, columns, and both diagonals: O(N^2) time.
- Discussed binary-search-per-row optimization giving O(N log N), then dismissed hashmap approaches for the static-board case.
- Proposed incremental approach: maintain row/column/diagonal counters per player, updated per move, giving O(1) win check per move.
- Outlined class structure: Board with update method, Game holding board and state, Driver class handling turns via CLI/UI hooks.
