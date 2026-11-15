# URL shortening service

## Problem

Create a short identifier for a long URL and serve redirects across regions. Handle collisions and consider whether sequential keys make other links easy to enumerate.

## Example

Two regions shorten different URLs at the same time. If both choose the same short key, a redirect is ambiguous unless allocation or collision resolution establishes one owner.

## Recorded approach

The initial proposal used truncated hashes and retried collisions. The discussion moved to region prefixes, pre-generated keys and an atomic counter, then considered enumeration of sequential IDs.

### Design: URL Shortener

- Started with hashing (SHA-256 truncated) plus collision retries, then region-prefixed IDs for multi-region setups.
- The interviewer pushed toward avoiding collisions entirely: pre-generated keys, then an atomic counter (etcd or Zookeeper).
- Sequential counters let attackers enumerate and copy all URLs; fix is adding a non-predictable random factor.

[Anonymized session notes](../../../sources/notes/designs/url-shortening-service.md)
