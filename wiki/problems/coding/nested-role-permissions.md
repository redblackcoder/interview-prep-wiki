# Nested role permissions

## Problem

Evaluate a permission inherited through nested roles. A higher-level explicit decision may override an inherited one. Combine the user's assigned roles and default to deny when none grants access.

## Example

Suppose one assigned role lacks read permission and a second role inherits a grant. The OR across the user's roles allows the read. The priority-weighted follow-up changes which matching rule determines the answer.

## Recorded approach

The Java evaluator traversed nested roles with DFS, applied higher-level overrides, combined user roles with OR and denied by default.

The session records passing tests for the nested-role Java evaluator. For a priority-weighted variant, the proposed follow-up was to collect matching permission rules and select the highest-priority match.

[Anonymized session notes](../../../sources/notes/coding/nested-role-permissions.md)
