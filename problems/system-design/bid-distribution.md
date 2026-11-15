# Bid distribution

Distribute changing bid values to several independent serving clusters.

## Recorded approach

Publish chunked bid deltas keyed by advertiser. Each cluster applies updates locally and can recover from a snapshot plus later offsets.

[Anonymized session notes](https://github.com/redblackcoder/interview-prep-raw/blob/master/docs/designs/bid-distribution.md)
