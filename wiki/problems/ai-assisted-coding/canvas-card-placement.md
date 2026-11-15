# Canvas card placement

## Problem

Build a list of homepage cards under two policies. The first ranks by recency with ads in positions 3 and 8. The second ranks by score, puts weather in position 2 and an ad in position 5, and drops surplus weather.

## Example

With two weather cards on the second canvas, keep the highest-ranked one for slot 2. Dropping unsupported cards must not shift the ad away from slot 5. A missing mandatory card makes the build fail.

## Recorded approach

The implementation used a fixed-size array, separate ad and content counters, and canvas-specific placement rules. The recorded tests exposed a one-based indexing error.

### Interview Problem and Clarifications

- Task: homepage layout service producing ordered card lists per canvas; evaluation focused on how AI is used for coding
- Canvas A: recency ranking, news/sports/weather, ads fixed at positions 3 and 8 (ads also ordered by recency)
- Canvas B: score ranking, news and weather only, weather at position 2, ad at position 5, extra weather cards discarded
- Unsupported card types are dropped; build fails if a required placed card (e.g. weather) is missing

### Implementation with AI Assistant

- AI-generated code was overly complex; the candidate rewrote it with a fixed-size array and separate ad/content counters
- The candidate refactored supported types into the interface and noted a future enum refactor for card types
- AI-generated tests exposed an index bug: positions are 1-indexed, fixed before time ran out

[Anonymized session notes](../../../sources/notes/coding/canvas-card-placement.md)
