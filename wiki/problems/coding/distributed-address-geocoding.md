# Distributed address geocoding

## Problem

Replace unreliable coordinates in a large input file using an external geocoder. Rows can share an address and different workers may read them. Avoid repeated lookups for the same address.

## Example

If two input partitions contain the same address, separate local caches each miss. Shuffling by address first sends both rows to one worker, which can resolve the address once and use the result for both rows.

## Recorded approach

Partition input by address hash and shuffle matching keys to the same worker for geocoding.

The initial approach used a sharded address map. Discussion exposed a race when two workers encountered an unseen address while a geocoding request was in flight. The final staged approach shuffled addresses by hash before lookup so all occurrences reached one worker. Each worker wrote its slice of the result, and those outputs were combined.

[Anonymized session notes](../../../sources/notes/coding/distributed-address-geocoding.md)
