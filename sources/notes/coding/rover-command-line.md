# Rover command line

Anonymized session notes.

## Rover CLI Implementation

- Chose Java + Picocli; interactive loop with Rover, State, Command abstractions driven by a controller.
    - Considered functional state-transition style but preferred OO for a game-like model.
- Assistant-generated code used coordinate-encoded directions; noted coupling but kept it, added direction tests.
- Refactored into state/ and commands/ subfolders; switched state from immutable record to mutable class.

## Obstacles Extension

- Added GameBoard with HashSet of obstacle points; move blocked with user message on collision.
    - Rejected quad tree: point lookups favor hash set over range-query structures.
- Open cleanups: unify Rover position with Point type; clarify board bounds are visual, not gameplay limit.
