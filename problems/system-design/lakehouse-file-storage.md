# Lakehouse file storage

Build an immutable-file service with a mutable namespace on ordinary storage nodes.

## Recorded approach

The metadata service maps a logical path to storage-file extents, then to storage nodes. Clients obtain read or write handles and transfer bytes to the storage service.

[Original whiteboard (anonymized export)](https://github.com/redblackcoder/interview-prep-raw/blob/master/docs/designs/lakehouse-file-storage.md)
