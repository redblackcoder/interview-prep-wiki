# Durable single-node key-value store

Keep concurrent point reads and writes recoverable after a process or machine crash.

## Solution summary

Order updates through a write-ahead log and publish in-memory values after durable flush.

[Full solution in interview-prep-raw](https://github.com/redblackcoder/interview-prep-raw/blob/master/docs/designs/durable-single-node-key-value-store.md)

[Java store implementation](https://github.com/redblackcoder/interview-prep-raw/blob/master/code/lsm-kv-store/src/main/java/com/practice/lsm/KVStore.java)
