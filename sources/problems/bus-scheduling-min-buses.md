---
source: code/bus-schedule-min-buses/
source_url: https://github.com/redblackcoder/interview-prep-raw/blob/master/code/bus-schedule-min-buses/
type: code
date_extracted: 2026-10
topic: Technical interview prep for senior roles
---

# Minimum Buses for a Schedule — Coding Round

**Round:** classic coding, 1 hour, CoderPad, Java.

## Problem as posed
1. **Part 1:** one station, trips given as (depart, arrive). Find the minimum number of buses. Overnight trips are optional: either don't support them or support them as a bonus. Write down every assumption about the input instead of validating it.
2. **Part 2:** several stations, trips given as (from, depart, to, arrive). Follow-up: an idle bus can be repositioned between any two stations in exactly 1 hour.

## Solution path
- Times are within one day with no wrap past midnight; intervals are half-open; assumptions go in as comments.
- The picture: plot every trip as a line, move a vertical line across, and the most lines it cuts at once is the answer.
- **Pitfall:** "for each interval i, count the intervals that overlap it" is a different quantity (and O(n^2)). On `{9,15},{9,11},{11,13},{13,14},{15,17}` it suggests 4, but 2 buses are enough.
- Buses are interchangeable once back — think of them as leases handed out at departure and returned at arrival. That gives +1/−1 events sorted with arrivals first on ties. Correct, O(n log n) time and O(n) space.
- **Part 2:**
  - Per-station counters, with departures as +1 and arrivals as −1, are correct when there's no repositioning; a negative counter just means spare buses.
  - With repositioning: buses that arrived within the last hour are local to their station, and older ones are fungible across all stations.

## Key Ideas
- "Fewest buses for a schedule" equals "the most trips in flight at one moment". The count only changes at departures and arrivals, so you only need to process those endpoints.
- Two equivalent implementations:
  - an events sweep (+1 at each departure, −1 at each arrival, arrivals first on ties);
  - sort by departure and keep a min-heap of arrival times; reuse the top bus if it's back.
- **Multiple stations, no repositioning:** each station needs as many buses as its worst deficit (departures so far minus arrivals so far). A negative balance means spare buses. Sum over stations.
- **With repositioning (uniform C):** take a local bus first, newest first; otherwise any bus idle for C or longer; otherwise a new bus. It's safe by an exchange argument: a bus idle for C or longer can do everything a local bus can.
- **General travel times:** minimum path cover, which equals n − max bipartite matching. The raw code uses this to check every greedy.

## My Understanding
- Turning the picture into code gives the algorithm directly. The count changes only when the line crosses a start or an end, so sort the endpoints and walk them.
- Work out expected test outputs by hand from the definition, never from the code.

## Open Questions
- Supporting overnight trips: split each into two intervals across midnight, or treat time as circular, if the schedule repeats daily.
- Exchange arguments: for "does this choice affect the future?", answer with a swap argument, not "it shouldn't matter".

## Connections
- Relates to: [[wiki/coding-patterns/sweep-line-resource-scheduling]]: the pattern page built from this extract
- Relates to: [[wiki/system-design-concepts/lease-and-fencing]]: buses as leases handed out and returned
- Relates to: [[wiki/coding-patterns/exactly-k-window-counting]]: both come down to checking the code against its own definition
