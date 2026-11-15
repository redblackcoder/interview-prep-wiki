# Distributed address geocoding

Anonymized session notes.

The initial approach used a sharded address map. Discussion exposed a race when two workers encountered an unseen address while a geocoding request was in flight. The final staged approach shuffled addresses by hash before lookup so all occurrences reached one worker. Each worker wrote its slice of the result, and those outputs were combined.
