# Model workflow platform

## Problem

Run shared workflows that gather context, call models, validate outputs and write results. Support both interactive requests and large offline batches.

## Example

A model call succeeds but writing the output fails. Recovery needs to know which step completed and whether replaying the write can duplicate a side effect.

## Recorded approach

Author workflows as DAGs, validate them before deployment, and execute online and batch runs separately. The design covers tenant isolation and resuming failed steps.

[Original whiteboard](../../../sources/whiteboards/model-workflow-platform.drawio)

### Workflow Authoring

- The candidate proposed workflows as DAGs with shared, platform-owned plugins (LLM calls, profile updates) and enforced steps like verification.
- Two authoring options: code-based workflow builder or YAML DSL compiled by a CI pipeline.
    - Picked YAML as north star for validation and platform control; downside is extending the DSL for every new need
- LLM plugin would hide model choice behind a string; batching multiple LLM calls considered for latency.
- Deployment flow: commit YAML, GitHub action generates workflow, deploy to staging, bake and validate, promote.

### Execution Architecture

- Online requests go through a gateway to a stateless platform service on Kubernetes; offline 1 billion user jobs run as Spark batch jobs.
- This implies two engines, one online and one offline; key value is the abstraction layer for partners.
- Noisy neighbors handled by global and per-tenant rate limits, sandboxed worker pods, dedicated pods for big partners, circuit breakers.

### Failure Handling

- Platform tracks job-to-worker state in a DB and watches pod health via etcd (said as 'XCD') to reassign incomplete jobs.
- For partial failures: idempotent side effects, or checkpointing steps and resuming, using Temporal-like tech.


[Anonymized session notes](../../../sources/notes/designs/model-workflow-platform.md)
