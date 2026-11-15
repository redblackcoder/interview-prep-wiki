# Distributed job scheduler

Run scheduled and ad hoc jobs for many tenants with bounded start delay and retry support.

## Solution summary

Claim due executions with leases and fence stale workers on every state change.

[Full solution in interview-prep-raw](https://github.com/redblackcoder/interview-prep-raw/blob/master/docs/designs/distributed-job-scheduler.md)
