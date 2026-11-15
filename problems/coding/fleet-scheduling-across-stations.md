# Fleet scheduling across stations

Minimize the fleet when trips start and end at different stations, with optional empty repositioning.

## Solution summary

Without repositioning, sum the largest deficit at each station. With arbitrary travel times, link compatible trips in a DAG and compute minimum path cover via maximum bipartite matching.

[Full solution in interview-prep-raw](https://github.com/redblackcoder/interview-prep-raw/blob/master/code/bus-schedule-min-buses/Solution.java)
