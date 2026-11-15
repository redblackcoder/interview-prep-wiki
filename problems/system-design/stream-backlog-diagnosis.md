# Stream backlog diagnosis

Recover a partitioned event pipeline whose lag grows while consumers appear healthy.

## Recorded approach

Investigate hot partitions and retrying sources. The recorded proposals were key salting, incorporating session identity, and throttling the offending source.

[Anonymized session notes](https://github.com/redblackcoder/interview-prep-raw/blob/master/docs/designs/stream-backlog-diagnosis.md)
