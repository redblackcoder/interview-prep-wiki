# Digital evidence archive

## Problem

Accept large device recordings, verify integrity and support authorized search. Each protected operation needs a durable custody record.

## Example

A device finishes a multipart upload but one chunk changes in transit. The archive needs to detect the change before publishing the recording; later access must leave an auditable event.

## Recorded approach

Verify hashes and device signatures on upload, log custody changes in a hash chain, and filter search by access policy. The audit discussion raised an atomicity gap.

### Upload

The proposed upload flow signed the content hash with the device key. A server-managed signature was considered for devices without that capability. The discussion compared presigned upload URLs with an upload proxy.

### Audit and search

The proposal sent hash-chained events through a tenant-partitioned queue to an append-oriented audit store. Ingress logging alone could record a request whose operation later failed. A transactional outbox was proposed during the discussion. For search, a policy engine filtered indexed results before returning them.

[Anonymized session notes](../../../sources/notes/designs/digital-evidence-archive.md)
