# Restaurant reservations

Anonymized whiteboard notes.

Flow:
```
Restaurant -> book a table with certain capacity & time -> "confirmed" or "not available"
```

## Data model
```
Table
  id
  max_capacity

Reservation
  id
  start_time
  end_time
  table_id

Request
  { start_time: 1, end_time: 4, capacity: 6 }
```

## Step 1 — find available tables
Find tables big enough that are NOT already reserved in the requested window.

```sql
SELECT *
FROM Table t
LEFT OUTER JOIN Reservation r ON t.id = r.table_id
WHERE t.max_capacity >= :capacity
  AND :start_time <= r.end_time
  AND :end_time   >  r.start_time;
```

**Interval-overlap condition.** Two intervals [s1,e1] and [s2,e2] overlap iff `s1 < e2 AND e1 > s2`. This single condition covers all four overlap cases (verified on the board):
```
// request fully inside an existing reservation   (+)
// request overlaps the start of a reservation    (+)
// request overlaps the end of a reservation      (+)
// request fully covers a reservation             (+)
```
Note: the WHERE above expresses "reservation that conflicts." To *find free tables* you either negate this (no conflicting reservation exists — cleaner as `NOT EXISTS`) or use the LEFT JOIN and keep rows where the join produced no overlapping reservation. This was the shaky part under time pressure — the join-vs-negation direction wasn't fully nailed.

## Step 2 — reserve one of the available tables
A second query INSERTs a reservation for one chosen table. This is where **concurrency** matters.

## Concurrency — avoiding double booking (the key discussion)
Two requests can both read the same table as "available" in Step 1, then both try to book it in Step 2.

- **Naive:** `SELECT ... FOR UPDATE` to lock all candidate rows. Correct but coarse — locks many rows, serializes bookings, hurts throughput. Rejected.
- **Idea raised on the board:** don't split availability across two tables. Keep a per-table **bitmask** of fixed time slots (e.g. 15-min windows across the day; 0 = free, 1 = booked). Query selects a table where the requested slots are free via a bitmask op, and the reserve step locks only *that one row* to flip the bits. Avoids the broad lock and the two-table join.
  - (Hand-wavy under time; doesn't cleanly handle variable-length reservations, day boundaries, or how the bitmask interacts with capacity. Flagged as incomplete.)
