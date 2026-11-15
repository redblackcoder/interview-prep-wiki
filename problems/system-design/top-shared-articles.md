# Top shared articles

Find the most shared articles over several rolling time windows.

## Recorded approach

Bucket shares by article and minute in memory, checkpoint each minute and retain an active/passive pair. The board estimates the time to compute and merge partial top-k results.

[Original whiteboard (anonymized export)](https://github.com/redblackcoder/interview-prep-raw/blob/master/docs/designs/top-shared-articles.md)
