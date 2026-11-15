# Operational counter collection

Aggregate application counter increments for time-window queries.

## Recorded approach

Preaggregate counter increments in a node agent and queue batches ahead of collectors. The design accepts edge loss for operational metrics and stores the resulting series in an append-oriented database.

[Anonymized session notes](https://github.com/redblackcoder/interview-prep-raw/blob/master/docs/designs/operational-counter-collection.md)
