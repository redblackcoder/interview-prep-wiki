# Digital evidence archive

Anonymized session notes.

## Upload

The proposed upload flow signed the content hash with the device key. A server-managed signature was considered for devices without that capability. The discussion compared presigned upload URLs with an upload proxy.

## Audit and search

The proposal sent hash-chained events through a tenant-partitioned queue to an append-oriented audit store. Ingress logging alone could record a request whose operation later failed. A transactional outbox was proposed during the discussion. For search, a policy engine filtered indexed results before returning them.
