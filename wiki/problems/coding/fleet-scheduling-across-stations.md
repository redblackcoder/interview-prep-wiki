# Fleet scheduling across stations

## Problem

Trips have origin, departure time, destination and arrival time. Minimize the initial fleet when buses may continue locally; then allow an idle bus to move to another station after a transfer delay.

## Example

A bus reaching B at 09:30 can depart from B immediately. If transfer takes one hour, it cannot start a trip from C at 10:00. Counting overlapping trips alone misses that location constraint.

## Recorded approach

Without repositioning, sum the largest deficit at each station. With arbitrary travel times, link compatible trips in a DAG and compute minimum path cover via maximum bipartite matching.

[Source material](../../../sources/code/bus-schedule-min-buses/Solution.java)

## Study notes

For a uniform transfer delay, a newly arrived bus can be reused locally, while a sufficiently long-idle bus can serve any station. Prefer an eligible local bus to preserve the flexible pool.

[Related analysis](minimum-fleet-for-a-timetable.md)
