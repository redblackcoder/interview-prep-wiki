# Distributed ID generation

Anonymized candidate design notes.

Combine counter+timestamp: `version+region ~10b | timestamp ~41b | sequence`. Pre-generate via a **leader "generator"** writing one key **per millisecond** into **Redis**; callers `INCR`+`GET` the current-ms key to claim a sequence number, then assemble `version | region | time | sequence`. Bound Redis with a few-seconds **TTL** on ms-keys (only ~2–3K live keys). Multi-AZ Redis; ~3 GUID handlers across AZs; single active generator + passive standby via K8s.

Worker keeps **last-generated in memory and persisted on disk**, refuses to go back in time, survives restarts without losing sequence.

Register worker with zk/etcd, **heartbeat** to keep the lease fresh; missed heartbeat → marked unhealthy → **removed from LB** so it gets no requests; a new worker takes the now-expired id. Bounded workers ⇒ bounded ids ⇒ fit the bits.
