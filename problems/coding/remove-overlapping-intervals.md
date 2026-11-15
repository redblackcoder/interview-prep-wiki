# Remove overlapping intervals

Delete the fewest intervals so the survivors do not overlap.

## Recorded approach

The board explores deletion heuristics and a suffix DP. Its written keep branch skips conflicting intervals without charging those deletions, so the recorded recurrence is incomplete.

[Original whiteboard (anonymized export)](https://github.com/redblackcoder/interview-prep-raw/blob/master/code/exercise-notes/remove-overlapping-intervals.md)
