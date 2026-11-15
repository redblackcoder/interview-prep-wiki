# Shared inference capacity

Share a fixed number of concurrent inference slots among latency-sensitive features.

## Solution summary

Serve priority tiers in order and share freed slots by weight among eligible requests in a tier.

[Full solution in interview-prep-raw](https://github.com/redblackcoder/interview-prep-raw/blob/master/docs/designs/shared-inference-capacity.md)
