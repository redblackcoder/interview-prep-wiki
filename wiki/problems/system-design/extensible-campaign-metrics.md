# Extensible campaign metrics

## Problem

Build periodic campaign dashboards whose metrics and grouping dimensions can evolve. Define the grain of the facts before joining descriptive dimensions.

## Example

A dashboard adds bidding strategy as a breakdown. The pipeline needs a consistent way to attach the strategy to each campaign fact without multiplying counts.

## Recorded approach

Aggregate structured campaign logs in micro-batches. A star schema and configuration-driven contract allow the dashboard to add dimensions and metrics.

### Design: Extensible Metrics Framework

- Batch case (dashboard within one hour): Spark micro-batches write aggregates to warehouse, queried via an engine (sounded like 'Dremio')
- Star schema: fact table at campaign ID grain, separate dimension tables like market and bidding strategy
- Structured logs and a config-driven contract let new metrics and dimensions be added without editing Spark jobs
- If joins get slow, add pipelines pushing into a faster database

[Anonymized session notes](../../../sources/notes/designs/extensible-campaign-metrics.md)
