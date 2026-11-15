# Rover command line

## Problem

Maintain a rover's coordinates and heading across interactive turn and movement commands. A board supplies obstacle coordinates. Turns change direction in place and a blocked move leaves the position unchanged.

## Example

Facing north at (0,0) with an obstacle at (0,1), a forward step is blocked. Turning right and taking one step then reaches (1,0). The turn must still work after the collision.

## Recorded approach

The Java CLI used a controller, rover state and commands. The obstacle extension checked a hash set of points before moving.

### Rover CLI Implementation

- Chose Java + Picocli; interactive loop with Rover, State, Command abstractions driven by a controller.
    - Considered functional state-transition style but preferred OO for a game-like model.
- Assistant-generated code used coordinate-encoded directions; noted coupling but kept it, added direction tests.
- Refactored into state/ and commands/ subfolders; switched state from immutable record to mutable class.

### Obstacles Extension

- Added GameBoard with HashSet of obstacle points; move blocked with user message on collision.
    - Rejected quad tree: point lookups favor hash set over range-query structures.
- Open cleanups: unify Rover position with Point type; clarify board bounds are visual, not gameplay limit.

[Anonymized session notes](../../../sources/notes/coding/rover-command-line.md)
