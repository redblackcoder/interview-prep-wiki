import java.util.*;

/*
 * Coding interview problem: minimum number of buses to run a schedule.
 *
 * Part 1 (single station): schedules[i] = {depart, arrive}, half-open [depart, arrive):
 *   a bus that arrives at 11 can leave again at 11. Times are within one day (no wrap).
 *   = Meeting Rooms II (LC 253 / LC 2406). Answer = max number of trips in flight at once.
 *
 * Part 2 (multiple stations): trips[i] = {from, depart, to, arrive}.
 *   2a: no repositioning (a bus only moves on scheduled trips).
 *   2b: an idle bus can be repositioned between any two stations in exactly C time units.
 *
 * Every answer is cross-checked against the exact answer: min path cover of the
 * "trip j can follow trip i" DAG = n - max bipartite matching.
 */
class Solution {

  // ---------- Part 1 ----------

  // Sweep line: +1 at each departure, -1 at each arrival; arrivals sort before
  // departures at the same time (half-open). Answer = peak of the running sum.
  static int minBusesSweep(int[][] schedules) {
    int[][] events = new int[schedules.length * 2][];
    for (int i = 0; i < schedules.length; i++) {
      events[2 * i] = new int[] {schedules[i][0], +1};
      events[2 * i + 1] = new int[] {schedules[i][1], -1};
    }
    Arrays.sort(events, (a, b) -> a[0] != b[0] ? Integer.compare(a[0], b[0]) : Integer.compare(a[1], b[1]));
    int inFlight = 0, peak = 0;
    for (int[] e : events) {
      inFlight += e[1];
      peak = Math.max(peak, inFlight);
    }
    return peak;
  }

  // Heap of arrival times: sort by departure; reuse the earliest-returning bus if it is back.
  // The heap never shrinks, so its final size is the answer.
  static int minBusesHeap(int[][] schedules) {
    int[][] s = schedules.clone();
    Arrays.sort(s, Comparator.comparingInt(a -> a[0]));
    PriorityQueue<Integer> returns = new PriorityQueue<>();
    for (int[] trip : s) {
      if (!returns.isEmpty() && returns.peek() <= trip[0]) {
        returns.poll();
      }
      returns.offer(trip[1]);
    }
    return returns.size();
  }

  // ---------- Part 2 ----------

  // Event = {time, kind, tripIndex}; kind 0 = arrival, 1 = departure (arrivals first on ties).
  private static int[][] events(int[][] trips) {
    int[][] ev = new int[trips.length * 2][];
    for (int i = 0; i < trips.length; i++) {
      ev[2 * i] = new int[] {trips[i][1], 1, i};
      ev[2 * i + 1] = new int[] {trips[i][3], 0, i};
    }
    Arrays.sort(ev, (a, b) -> a[0] != b[0] ? Integer.compare(a[0], b[0]) : Integer.compare(a[1], b[1]));
    return ev;
  }

  // 2a: each station is independent. Buses needed at a station = its worst deficit
  // (departures so far - arrivals so far). A negative balance just means spare buses.
  static int minBusesNoReposition(int[][] trips) {
    Map<Integer, Integer> balance = new HashMap<>();
    Map<Integer, Integer> need = new HashMap<>();
    for (int[] e : events(trips)) {
      int[] t = trips[e[2]];
      if (e[1] == 0) {
        balance.merge(t[2], 1, Integer::sum);
      } else {
        int b = balance.merge(t[0], -1, Integer::sum);
        need.merge(t[0], -b, Math::max);
      }
    }
    int total = 0;
    for (int n : need.values()) total += Math.max(0, n);
    return total;
  }

  // 2b: an idle bus at station x that arrived at time a can serve
  //   - station x from time a (local), or
  //   - any station from time a + c (it could have been repositioned).
  // Greedy at each departure (s, t):
  //   1. an idle bus already at s, most recent arrival first (LIFO): an older bus can do
  //      everything a newer one can, so keep the older one;
  //   2. else any bus idle since <= t - c (all such buses are interchangeable);
  //   3. else a new bus.
  // Local before remote is safe by exchange: a remote bus that has been idle >= c can serve
  // any future trip the local bus could, so swapping them never hurts.
  static int minBusesWithReposition(int[][] trips, int c) {
    int n = trips.length;
    int[] arrival = new int[n];                   // idle-bus id = index of the trip it just finished
    int[] station = new int[n];
    boolean[] used = new boolean[n];
    Map<Integer, Deque<Integer>> idleAt = new HashMap<>();   // station -> idle ids, oldest first
    PriorityQueue<Integer> idleByTime = new PriorityQueue<>(Comparator.comparingInt(id -> arrival[id]));
    int buses = 0;

    for (int[] e : events(trips)) {
      int[] t = trips[e[2]];
      if (e[1] == 0) {                            // arrival: bus becomes idle at t[2]
        int id = e[2];
        arrival[id] = t[3];
        station[id] = t[2];
        idleAt.computeIfAbsent(t[2], k -> new ArrayDeque<>()).addLast(id);
        idleByTime.offer(id);
        continue;
      }
      int from = t[0], time = t[1];
      Deque<Integer> local = idleAt.get(from);
      if (local != null && !local.isEmpty()) {    // 1. local, newest first
        used[local.pollLast()] = true;
        continue;
      }
      while (!idleByTime.isEmpty() && used[idleByTime.peek()]) idleByTime.poll();
      if (!idleByTime.isEmpty() && arrival[idleByTime.peek()] + c <= time) {
        // 2. the globally oldest idle bus is the oldest at its own station
        int g = idleByTime.peek();
        used[idleAt.get(station[g]).pollFirst()] = true;
        continue;
      }
      buses++;                                    // 3. new bus
    }
    return buses;
  }

  // ---------- Exact answer: n - max matching ----------

  // Trip j can follow trip i on the same bus if the bus gets to j's origin in time.
  static int minBusesMatching(int[][] trips, long c) {
    int n = trips.length;
    List<List<Integer>> next = new ArrayList<>();
    for (int i = 0; i < n; i++) {
      List<Integer> adj = new ArrayList<>();
      for (int j = 0; j < n; j++) {
        if (i == j) continue;
        long ready = trips[i][3] + (trips[i][2] == trips[j][0] ? 0 : c);
        if (ready <= trips[j][1]) adj.add(j);
      }
      next.add(adj);
    }
    int[] matchR = new int[n];
    Arrays.fill(matchR, -1);
    int matched = 0;
    for (int i = 0; i < n; i++) {
      if (augment(i, next, matchR, new boolean[n])) matched++;
    }
    return n - matched;
  }

  private static boolean augment(int u, List<List<Integer>> next, int[] matchR, boolean[] seen) {
    for (int v : next.get(u)) {
      if (seen[v]) continue;
      seen[v] = true;
      if (matchR[v] == -1 || augment(matchR[v], next, matchR, seen)) {
        matchR[v] = u;
        return true;
      }
    }
    return false;
  }

  // ---------- Tests ----------

  public static void main(String[] args) {
    int[][] example = {{9, 11}, {10, 12}, {11, 13}};
    int[][] nested = {{9, 15}, {9, 11}, {11, 13}, {13, 14}, {15, 17}};   // the round's test: 2, not 4
    System.out.println("example sweep=" + minBusesSweep(example) + " heap=" + minBusesHeap(example));
    System.out.println("nested  sweep=" + minBusesSweep(nested) + " heap=" + minBusesHeap(nested));

    Random r = new Random(42);
    int bad1 = 0, bad2a = 0, bad2b = 0;
    for (int it = 0; it < 20000; it++) {
      int n = 1 + r.nextInt(9), stations = 1 + r.nextInt(3), c = 1 + r.nextInt(3);
      int[][] trips = new int[n][];
      for (int i = 0; i < n; i++) {
        int dep = r.nextInt(10), arr = dep + 1 + r.nextInt(4);
        trips[i] = new int[] {r.nextInt(stations), dep, r.nextInt(stations), arr};
      }
      int[][] oneStation = new int[n][];
      for (int i = 0; i < n; i++) oneStation[i] = new int[] {0, trips[i][1], 0, trips[i][3]};
      int[][] intervals = new int[n][];
      for (int i = 0; i < n; i++) intervals[i] = new int[] {trips[i][1], trips[i][3]};

      int exact1 = minBusesMatching(oneStation, 0);
      if (minBusesSweep(intervals) != exact1 || minBusesHeap(intervals) != exact1) bad1++;
      if (minBusesNoReposition(trips) != minBusesMatching(trips, Integer.MAX_VALUE)) bad2a++;
      if (minBusesWithReposition(trips, c) != minBusesMatching(trips, c)) bad2b++;
    }
    System.out.println("mismatches vs matching: part1=" + bad1 + " part2a=" + bad2a + " part2b=" + bad2b);
  }
}
