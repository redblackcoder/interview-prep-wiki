# URL shortening service

Anonymized session notes.

## Design: URL Shortener

- Started with hashing (SHA-256 truncated) plus collision retries, then region-prefixed IDs for multi-region setups.
- The interviewer pushed toward avoiding collisions entirely: pre-generated keys, then an atomic counter (etcd or Zookeeper).
- Sequential counters let attackers enumerate and copy all URLs; fix is adding a non-predictable random factor.
