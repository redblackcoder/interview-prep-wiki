# Durable single-node key-value store

Keep concurrent point reads and writes recoverable after a process or machine crash.

## Recorded approach

Serialize WAL writes ahead of map updates and replay the log on restart. Group commit was discussed as a way to share flush costs.

[Anonymized session notes](https://github.com/redblackcoder/interview-prep-raw/blob/master/docs/designs/durable-single-node-key-value-store.md)

[Saved Java implementation](https://github.com/redblackcoder/interview-prep-raw/blob/master/code/lsm-kv-store/src/main/java/com/practice/lsm/KVStore.java)
