# Shared inference capacity

Share a fixed number of concurrent inference slots among latency-sensitive features.

## Recorded approach

Use priority tiers and weighted sharing among waiting calls. Compare a shared counter with per-worker allocations and bound pending requests by timeout.

[Anonymized session notes](https://github.com/redblackcoder/interview-prep-raw/blob/master/docs/designs/shared-inference-capacity.md)
