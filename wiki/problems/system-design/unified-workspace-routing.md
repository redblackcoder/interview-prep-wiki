# Unified workspace routing

## Problem

Give a user one entry point for workspaces hosted on different backends. Determine the authorized workspace and route the request to its serving region.

## Example

A user can open workspaces A and B but a third workspace ID also resolves in the directory. Routing to a valid backend alone cannot establish permission to enter it.

## Recorded approach

Use a central landing page and a routing proxy keyed by workspace and identity. The discussion compared geo-DNS and Anycast and left cross-workspace authorization open.

### Hypothetical: Unifying Workspace URLs

- The candidate proposed a central landing page, then a routing proxy keyed on auth and workspace ID
- Discussed geo-DNS vs Anycast for proxy placement; cross-workspace auth was the weaker area

[Anonymized session notes](../../../sources/notes/designs/unified-workspace-routing.md)
