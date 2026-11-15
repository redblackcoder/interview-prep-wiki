# Conversational model serving

Stream responses for multi-turn conversations under a fixed inference budget.

## Recorded approach

Hold the client stream open for token delivery and queue requests when generation capacity is exhausted. The discussion surfaced the need to route queued responses back to the waiting connection.

[Anonymized candidate design notes](https://github.com/redblackcoder/interview-prep-raw/blob/master/docs/designs/conversational-model-serving.md)
