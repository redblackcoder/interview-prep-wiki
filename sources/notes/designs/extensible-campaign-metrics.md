# Extensible campaign metrics

Anonymized session notes.

## Design: Extensible Metrics Framework

- Batch case (dashboard within one hour): Spark micro-batches write aggregates to warehouse, queried via an engine (sounded like 'Dremio')
- Star schema: fact table at campaign ID grain, separate dimension tables like market and bidding strategy
- Structured logs and a config-driven contract let new metrics and dimensions be added without editing Spark jobs
- If joins get slow, add pipelines pushing into a faster database
