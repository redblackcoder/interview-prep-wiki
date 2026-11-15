# Merge sorted arrays in place

Anonymized session notes.

## Coding: In-Place Merge of Two Sorted Arrays

- Problem: merge second sorted array into first (which has enough trailing free space) in place.
- The candidate proposed forward insertion with batch shifts sized to the gap between adjacent list-one elements.
- Worst-case complexity is O(M\*N) due to repeated shifting; considered filling from the right for O(M+N).
- Did not fully land the reverse-fill approach within time.
