# Sweep Line and Resource Scheduling

These questions ask **"how many resources do I need so every interval gets one?"**: buses for trips, rooms for meetings, servers for jobs, chairs for guests. The answer is the **most intervals active at one moment**. Picture a vertical line sweeping across the timeline; the most intervals it cuts at once is the answer. The count only changes at an interval's start or end, so you **process only the endpoints**.

The follow-ups add state to the same time-ordered loop: free and busy pools, multiple locations, travel time. The general case is a matching problem.

## Recognizing it
- "Minimum number of X to cover all intervals", "max concurrent", "peak overlap", "can all bookings fit in capacity c".
- Resources are **interchangeable once released**: a returned bus is just a bus. That's what lets you replace intervals with +1/−1 events.

## Core: two equivalent implementations

**Events sweep:** +1 at each start and −1 at each end, sorted by time. The tie-break is a decision you make deliberately (below).
```java
events: {start, +1}, {end, -1}
sort by (time, delta)          // -1 before +1 at the same time = half-open [start, end)
run += delta; peak = max(peak, run)
```

**Heap of end times:** sort by start, and keep a min-heap of when each resource frees up.
```java
sort by start
for each interval: if (!heap.isEmpty() && heap.peek() <= start) heap.poll();
                   heap.offer(end);
answer = heap.size()           // the heap never shrinks
```
Both are O(n log n) time and O(n) space. Use the heap when you also need **which** resource gets assigned (LC 1942, 2402, 1882), because the heap holds the actual resources.

**Tie-break = interval semantics.** For half-open `[s, e)` (a bus back at 11 can leave at 11), ends go before starts. For closed `[s, e]` (LC 2406), starts go before ends. Say which one you're using out loud.

**The wrong algorithm to avoid:** for each interval i, counting how many intervals overlap it. That's O(n^2), and it measures a different thing. `[9,15]` overlaps three intervals, but those three are one after another, so one bus could run all of them. The vertical-line picture is right; code that picture.

## Follow-up 1: multiple locations, no repositioning
Trips go `(from, depart, to, arrive)`, and buses only move on trips. Each station is independent, so keep a balance per station: arrivals minus departures so far. A station needs as many buses as its worst deficit, and the answer is the sum over stations. **A negative balance is fine**: it just means spare buses are parked there.

## Follow-up 2: repositioning with uniform travel time C
An idle bus that arrived at station x at time a can serve x from time a, and any station from a + C. Walk through events in time order, arrivals first on ties. At each departure (s, t):
1. use an **idle bus already at s, newest arrival first**;
2. otherwise use **any bus idle since t − C or earlier**; they're all interchangeable;
3. otherwise add a new bus.

**Why the greedy is safe (exchange argument):** a bus idle for C or longer can serve any future trip a local bus could. So if an optimal solution used a remote bus here and the local bus later, swapping them is still valid. Among local buses, the older one becomes usable everywhere sooner, so spend the newest.

State:
- a deque per station of idle buses, oldest first (spend from the back);
- one min-heap of all idle buses by arrival time;
- lazy deletion via a `used[]` array.

The globally oldest idle bus is always at the front of its own station's deque. Cost is O(n log n).

This is the answer to "does the choice affect the future?". **Yes**, the choice matters, and the rule above is the one that's provably safe.

## General case: travel times differ by station pair
The greedy no longer holds. Build a DAG with an edge i → j when one bus can do trip i and then trip j: `arrive_i + travel(to_i, from_j) <= depart_j`. Buses are vertex-disjoint paths, so you need a **minimum path cover**, which equals **n − max bipartite matching**. That's O(V·E) with Kuhn's algorithm, or O(E·√V) with Hopcroft–Karp. It's worth naming even if you never code it, and it makes a good brute-force checker for the greedy.

## Traps
- **Test against the definition, not the code.** Work out the expected answer by sweeping the line by hand. On `{9,15},{9,11},{11,13},{13,14},{15,17}` the answer is 2; the overlap-count code says 4.
- Get the tie-break wrong and adjacent intervals double-count.
- Assuming times fall on the hour and stepping a clock through time. Process events instead; times can be arbitrary.
- Overnight trips: either state "no wrap past midnight" as an assumption, or split each trip at midnight. If the schedule repeats daily, treat time as a circle.

## Practice ladder
| # | Problem | What it adds |
|---|---|---|
| 1854 | Maximum Population Year | +1/−1 events in their simplest form |
| 2406 | Divide Intervals Into Minimum Number of Groups | **the bus problem** (free version of 253; closed intervals flip the tie-break) |
| 253 | Meeting Rooms II (premium) | the canonical version |
| 1094 | Car Pooling | events carry weights |
| 1109 | Corporate Flight Bookings | difference array (a sweep over fixed indices) |
| 732 | My Calendar III | max overlap as bookings arrive one at a time (TreeMap of deltas) |
| 2251 | Number of Flowers in Full Bloom | a sweep that also answers point queries |
| 1942 | The Number of the Smallest Unoccupied Chair | freed chairs go back into a pool |
| 1882 | Process Tasks Using Servers | free and busy heaps, closest to follow-up 2 |
| 2402 | Meeting Rooms III | two heaps plus delayed starts |
| 435 / 452 | Non-overlapping Intervals / Min Arrows | greedy by end time with an exchange argument |
| 218 | The Skyline Problem | a sweep plus a max-heap (stretch) |

## Connections
- [lease and fencing](../system-design-concepts/lease-and-fencing.md): treat buses as leases: capacity handed out at departure and returned at arrival. Peak outstanding leases is the answer.
- [serving constrained resources](../system-design-concepts/serving-constrained-resources.md): the same "how many slots are in use at once" question at the scale of a system design
- [littles law](../theory/littles-law.md): average in-flight equals arrival rate times duration. The sweep gives the exact peak; Little's law gives the average for capacity planning.
- [exactly k window counting](exactly-k-window-counting.md): a sibling way to count by position, with endpoints that only move one way

## Sources
- [minimum fleet for a timetable](../problems/coding/minimum-fleet-for-a-timetable.md): the coding round: the problem, the overlap-count pitfall, and the multi-station follow-up
- Runnable code (sweep, heap, per-station, repositioning greedy, and a matching-based exact checker): [bus-schedule-min-buses/](../../sources/code/bus-schedule-min-buses)
