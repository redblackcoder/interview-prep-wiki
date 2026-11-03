# Read State & Watermarking: Durability and Multi-Device Sync

A real-time chat system built on in-memory fan-out ([[system-design-concepts/message-fanout]]) has two gaps that fan-out alone can't close: **messages must survive process/node loss** (in-memory delivery is best-effort), and **a user on multiple devices must not get re-notified for things already read elsewhere.** Both are solved by making the *durable log* the source of truth and tracking a per-user **read position (watermark)** — not by trusting the real-time layer.

## Durability lifecycle — the real-time path is an optimization, not the record
The delivered-over-WebSocket copy is the *fast* path; the database is the *true* path. The ordering of steps is what makes it safe:

1. **Client safety net.** On send, the client renders optimistically *and* starts a local retry timer. If the server never confirms, the client resends — so a dropped in-memory message isn't lost.
2. **Durable commit first.** The message reaches the guild process, which writes it to a durable store (Cassandra/ScyllaDB) **before** acknowledging.
3. **ACK, then fan out.** Only after the DB confirms does the server ACK the sender (stopping its retry timer) and fan the message out to online sessions.
4. **Offline gap-fill.** A reconnecting client knows its last-seen message ID and pulls everything newer straight from the DB (a REST/read path), *bypassing* real-time fan-out. Fan-out only ever needs to reach currently-connected clients; history is the DB's job.

This is [[theory/durability-rpo-rto|RPO]] thinking applied to chat: commit-before-ack drives the data-loss window toward zero, and the client retry timer + DB backfill are the reconciliation paths for the two ways delivery can fail (server drop before commit; client offline during fan-out).

## Watermarking — dedup notifications across devices
Deliver-and-durability still leaves the multi-device problem: read a message on mobile, and your desktop shouldn't badge/ping for it. Fix: a dedicated **Read State** service holding a per-user, per-channel **watermark** — the highest message ID the user has read (`channel X → message 2000`), in a highly-optimized key-value store.

- **On wake, fetch the watermark.** The client reconciles against its global read position.
- **Silent below, notify above.** Messages **older** than the watermark are inserted into the UI *silently* (they fill history but don't alert); only messages **newer** than the watermark trigger a badge or sound.

The watermark is a **monotonic cursor**, not a per-message read receipt — one small value per (user, channel) instead of an ack per message, which is what makes it cheap enough to read on every client wake across hundreds of millions of channels. It's the same "track a position, not every event" idea as a Kafka consumer offset or a WAL LSN.

## Key points
- The durable store is the source of truth; the real-time fan-out copy is a latency optimization that's allowed to fail.
- **Commit-before-ACK** (then fan out) is the ordering that bounds data loss; the client retry timer covers the pre-commit drop.
- Offline clients gap-fill from the DB by last-seen ID — fan-out only serves connected clients, so history scales independently of real-time.
- A **watermark** = one monotonic (user, channel) → last-read-ID cursor; older-than-watermark inserts silently, newer notifies. Dedups multi-device notifications cheaply.
- "Track a read position, not per-message receipts" is the same pattern as consumer offsets / WAL LSNs.

## Interview angle

> "Two problems fan-out can't solve: durability and multi-device notification dedup. For durability I make the database the source of truth — the client sends optimistically with a retry timer, the server commits to Cassandra *before* it ACKs, then fans out; an offline client just backfills from the DB by last-seen ID, so real-time only serves connected users. For dedup I keep a per-user, per-channel watermark — the highest message ID you've read — in a fast KV store. On wake, anything older than the watermark inserts silently to fill history, anything newer notifies. It's a monotonic cursor, one value per channel, not a read receipt per message — same idea as a Kafka offset."

## Connections
- [[system-design-concepts/message-fanout]] — the real-time delivery layer this makes durable and consistent; the two together are Discord's messaging design
- [[theory/durability-rpo-rto]] — commit-before-ACK + client retry is RPO≈0 reasoning; the retry timer is the non-idempotent-replay reconciliation
- [[system-design-concepts/rds-vs-key-value-store]] — the watermark is a textbook fast-KV use case (small hot value, read constantly) vs the durable message log in a wide-column store
- [[theory/consistent-hashing]] — the durable store (Cassandra/Scylla) partitions by the same style of hashing the rest of the system uses

## Sources
- [[sources/articles/discord-scaling-elixir-5m-concurrent]] — companion deep-dive §6 (Message Durability & Multi-Device Sync)
- [How Discord Scaled Elixir to 5,000,000 Concurrent Users](https://discord.com/blog/how-discord-scaled-elixir-to-5-000-000-concurrent-users) — original article context
