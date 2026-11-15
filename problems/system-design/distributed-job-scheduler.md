# Distributed job scheduler

Run scheduled and ad hoc jobs for many tenants with bounded start delay and retry support.

## Recorded approach

Shard by tenant, materialize due executions ahead of time, and let a dispatcher assign them to a driver that supervises sandboxed workers. The design left the atomic dispatch claim unresolved.

[Anonymized candidate design notes](https://github.com/redblackcoder/interview-prep-raw/blob/master/docs/designs/distributed-job-scheduler.md)
