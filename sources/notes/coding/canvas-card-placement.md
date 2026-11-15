# Canvas card placement

Anonymized session notes.

## Interview Problem and Clarifications

- Task: homepage layout service producing ordered card lists per canvas; evaluation focused on how AI is used for coding
- Canvas A: recency ranking, news/sports/weather, ads fixed at positions 3 and 8 (ads also ordered by recency)
- Canvas B: score ranking, news and weather only, weather at position 2, ad at position 5, extra weather cards discarded
- Unsupported card types are dropped; build fails if a required placed card (e.g. weather) is missing

## Implementation with AI Assistant

- AI-generated code was overly complex; the candidate rewrote it with a fixed-size array and separate ad/content counters
- The candidate refactored supported types into the interface and noted a future enum refactor for card types
- AI-generated tests exposed an index bug: positions are 1-indexed, fixed before time ran out
