# Minimum Buses for a Schedule

**Source:** coding interview round (1 hour). Write-up: wiki `sources/problems/bus-scheduling-min-buses`.

## Problem

**Part 1.** One station. Each trip is `{depart, arrive}`: the bus leaves the station and comes back. Find the minimum number of buses that can run the whole schedule.
- Assumptions I chose: times are within one day (no trips past midnight), and intervals are half-open, so a bus arriving at 11 can leave again at 11.
- This is LC 253 Meeting Rooms II, or LC 2406 Divide Intervals Into Minimum Number of Groups.

**Part 2.** Several stations. Each trip is `{from, depart, to, arrive}`.
- **2a:** buses only move on scheduled trips.
- **2b:** an idle bus can be repositioned between any two stations in exactly `C` (1 hour).

## Core ideas

- **Part 1, sweep line:** +1 at each departure and -1 at each arrival, with arrivals first when times tie. The answer is the peak of the running sum, in O(n log n).
  - Alternative: sort by departure and keep a min-heap of arrival times; reuse the top bus if it's back.
- **2a:** each station is independent. A station needs as many buses as its worst deficit (departures so far minus arrivals so far). A negative balance just means spare buses there. Sum over stations.
- **2b:** a bus that arrived at station x at time a can serve x from time a, and any station from time a + C. At each departure (s, t), take a bus in this order:
  1. an idle bus already at s, newest arrival first;
  2. otherwise, any bus idle since t − C or earlier (they're all interchangeable);
  3. otherwise, a new bus.
  - Why it's safe: a bus that has been idle for C or longer can do everything a local bus can, so using the local bus first never hurts. Among local buses, the older one becomes usable everywhere sooner, so keep it.
- **General case** (travel times differ by station pair): minimum path cover over the DAG "trip j can follow trip i", which equals n − max bipartite matching. `minBusesMatching` implements this, and the test uses it as the exact answer to check every greedy.

## Pitfalls

- "Count the intervals overlapping interval i" is O(n^2) and wrong: overlapping one interval doesn't mean they're all in flight at once. The picture to code is the sweep — move a vertical line and count how many intervals it cuts.
- Hand-check before trusting output: `{9,15},{9,11},{11,13},{13,14},{15,17}` needs 2 buses.
- Treat buses as leases: handed out at departure, returned at arrival. Peak outstanding leases is the answer, which is exactly the +1/−1 sweep.

## Files

- `Solution.java`:
  - `minBusesSweep` and `minBusesHeap` for part 1;
  - `minBusesNoReposition` for 2a and `minBusesWithReposition` for 2b;
  - `minBusesMatching` as the exact answer;
  - random cross-checks in `main`.

## Sample output

```
example sweep=2 heap=2
nested  sweep=2 heap=2
mismatches vs matching: part1=0 part2a=0 part2b=0
```
