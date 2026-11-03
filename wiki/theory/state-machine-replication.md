# State Machine Replication (SMR)

**State Machine Replication** is the principle that turns "keep N machines identical" into "agree on one ordered input log." It is the conceptual bridge between a **log** and **consensus**: consensus algorithms (Raft, Paxos, ZAB) exist precisely to produce the single ordered log that SMR consumes.

## The one-sentence mental model

> **Two identical, deterministic processes that start in the same state and consume the same inputs in the same order will produce the same output and end in the same state — so replicating a service reduces to replicating its input log.**

## How it works

Model any service as a **deterministic state machine**: a KV store applies `put`/`delete`; the arithmetic toy service applies `+1`, `*2`. Given the same commands in the same order, every replica walks the identical sequence of states.

So the replication problem collapses to one job: **build an append-only, totally-ordered log of commands and feed it to every replica.** The log's role is to **squeeze all non-determinism out of the input stream** — anything timing-dependent (`gettimeofday`, thread-scheduling order, random) must be resolved *once* and written into the log, never recomputed per replica.

```
        ┌── replica A: apply [c1,c2,c3,…] → state_A
 LOG ───┼── replica B: apply [c1,c2,c3,…] → state_B     all identical
        └── replica C: apply [c1,c2,c3,…] → state_C
```

Two flavors (Kreps' framing):
- **Active-active ("state machine model"):** log the *incoming commands*; every replica executes them. (Log `+1`, `*2`.)
- **Active-passive ("primary-backup"):** one leader executes and logs the *resulting state changes*; followers apply those. (Log `1`, `3`, `6`.)

Either way, **ordering is the whole game** — reorder a `+1` and a `*2` and replicas diverge. A replica's entire state is then captured by a single number: **the highest log index it has applied.**

## Key points
- SMR is *why* consensus matters: "agree on a value" is too weak; real systems need to agree on a **sequence** — hence a log, not a single-value register.
- Determinism is a hard requirement. Non-deterministic inputs must be **captured into the log**, not re-derived on each replica.
- The max-applied log index doubles as a **logical clock** for a replica's state — the basis for read-your-writes (wait until a serving node has applied up to your write's index).
- This is the same idea rebranded across communities: **"event sourcing"** (enterprise), **"atomic broadcast"** (distributed systems), **redo/WAL replay** (databases).
- KRaft (Kafka) is SMR applied to *cluster metadata*: the metadata topic is the command log, controllers are the replicas.

## Interview angle

> "State machine replication says: if a service is deterministic, then two replicas fed the same commands in the same order end in the same state. So I don't need to replicate the *state* — I replicate the *input log*. That's exactly what Raft or Paxos build: an agreed, totally-ordered log of commands. The subtlety is determinism — anything like a timestamp or random value has to be decided once and written into the log, not recomputed per replica, or they drift. And a replica's whole state is then summarized by the highest log index it's applied, which is how you get read-your-writes."

## Connections
- [[theory/consensus-raft]] — Raft/Paxos are the machinery that *produces* the single ordered log SMR assumes
- [[system-design-concepts/the-log-abstraction]] — SMR is the theoretical payoff of treating the log as the primitive
- [[system-design-concepts/table-log-duality]] — a replica's state (table) is the fold of the command log
- [[theory/durability-rpo-rto]] — event-sourcing replay is SMR in practice; note the non-idempotent-replay hazard
- [[system-design-concepts/leaderless-vs-leader-based]] — active-active vs primary-backup is the SMR framing of that write-path fork

## Sources
- [[sources/docs/the-log-jay-kreps]] — the State Machine Replication Principle and its active-active / primary-backup flavors
- [the-log-jay-kreps.md](https://github.com/redblackcoder/interview-prep-raw/blob/master/docs/the-log-jay-kreps.md) — Jay Kreps, "The Log" (2013)
