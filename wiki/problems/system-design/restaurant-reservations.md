# Restaurant reservations

## Problem

Design booking for a single restaurant whose tables seat different numbers of people. A request gives a party size, start time, and end time. Confirm one table that can seat the whole party for the full interval, or return “not available.” Several customers may book concurrently. Two confirmed reservations for the same table must never overlap.

## Walk through a booking

A party of six asks for 6:00–7:00. A four-seat table is too small. A six-seat table booked from 6:30–7:30 is large enough but occupied for part of the request. An eight-seat table with a booking that ends at 6:00 can take the party if intervals use an exclusive end time.

Now suppose two requests both find that last table free before either inserts a reservation. Both searches were correct when they ran. The booking operation must still confirm at most one of them.

## Recorded design

The whiteboard starts with two records: a table has an ID and maximum capacity, and a reservation has a table ID, start, and end. The search filters by capacity and checks for a conflicting reservation. For half-open intervals, a conflict exists when the requested start is earlier than the existing end **and** the requested end is later than the existing start. The SQL on the board was unfinished: it selected conflicts and used an inclusive comparison at one boundary. That distinction matters for the 6:00 handoff above.

The next step on the board was to insert a reservation for one chosen table. Locking every candidate would prevent simultaneous claims but could also make requests for different tables wait. The proposed alternative stored a bitmask of fixed time slots per table. A request could identify a candidate with all its required slots free, then lock the chosen row to claim those slots. For this to resolve the two-customer race, availability has to be checked again while holding that lock.

The sketch stopped before settling how to represent variable-length bookings or bookings that cross midnight.

## Later study notes

A later review explored letting the database reject overlapping reservations using an exclusion constraint on table ID and time range. That approach preserves arbitrary start and end times. The original whiteboard explored the slot-bitmask approach.

[Anonymized whiteboard and original SQL sketch](../../../sources/notes/restaurant-reservation-whiteboard.md) · [Later study notes on booking concurrency](../../reading-notes/combination-sum-and-reservation-interview.md)
