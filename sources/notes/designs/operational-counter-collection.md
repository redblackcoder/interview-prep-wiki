# Operational counter collection

Anonymized session notes.

## Metrics System Design

- Scoped to counters; prioritized low end-to-end latency over strict accuracy since not for billing
- Chose time series DB over relational; LSM-tree fits high append writes, in-memory recent data aids recent queries
    - Cassandra viable (metric as partition key, time as sort key) but weaker recent-window caching
- Pipeline: SDK → agent (daemon set, pre-aggregates ~1s) → queue → collectors → time series DB
    - Daemon set over sidecar to share memory across pods on a node
    - Queue in front of collectors enables dedup (host+time msg ID) and backpressure
- Considered UDP from service to agent: lossy delivery acceptable for operational metrics, opposite of queueing
