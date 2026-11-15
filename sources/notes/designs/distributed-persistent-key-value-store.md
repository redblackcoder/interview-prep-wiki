# Distributed persistent key-value store

Anonymized design notes.

A stateless compute node holds the partition ring, locates the owning storage replicas and gathers quorum responses. Range queries split at partition boundaries and merge the returned ranges.

The notes discuss three replicas across availability zones, two acknowledgments for writes and two replicas for reads. At the end, two coordination models remained on the board: a leaderless coordinator with conflict repair, or forwarding to a consensus leader. No final choice was recorded.
