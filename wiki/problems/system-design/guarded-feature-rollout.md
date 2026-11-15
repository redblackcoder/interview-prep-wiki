# Guarded feature rollout

## Problem

Let teams release frequently while detecting and reversing changes that hurt engagement. Make individual releases observable even when deployments overlap.

## Example

Engagement falls after several releases in one week. A gradual rollout with a comparison cohort can narrow the cause before the change reaches everyone.

## Recorded approach

The proposed rollout gradually increased exposure and rolled back on metric degradation. Enforced experiments and release scorecards came up in feedback.

The proposed approach was a gradual percentage rollout with metric-based automatic rollback. The discussion weighed the cost of slower releases against detecting engagement drops sooner. The interviewer suggested enforcing experiments and release scorecards.

[Anonymized session notes](../../../sources/notes/designs/guarded-feature-rollout.md)
