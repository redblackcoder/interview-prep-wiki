# Durability: RPO, RTO, and Recoverable Execution

When a system can crash mid-operation, two questions define its durability contract:

- **RPO (Recovery Point Objective)** — the maximum data you're willing to *lose*, measured in time or events. It answers "when I recover, how far back is my last good state?"
  - RPO = 5 min → you may lose up to 5 minutes of work.
  - **RPO ≈ 0** → you lose essentially nothing; every committed change is durably persisted (synchronous `fsync` / write-ahead log) *before* you acknowledge it.
- **RTO (Recovery Time Objective)** — how *long* recovery takes. RPO = how much you lose; RTO = how long you're down.

The cost knob: **lower RPO = synchronous fsync on the write path = slower writes.** You buy durability with latency.

## Choosing RPO per subsystem

RPO is not one global number — set it per subsystem by asking "what does losing this cost?":

- **Irreplaceable state** (a conversation transcript, a ledger) → RPO≈0, strongly consistent, durable.
- **Rebuildable state** (a search index, a cache) → RPO is irrelevant; lose it all and re-derive it.

Naming *two different postures in one system, and why each is earned*, is the senior signal. (See [[system-design-concepts/agent-loop|coding-agent]] persistence: transcript RPO≈0 vs. index as a throwaway cache.)

### RPO is also a dial you turn *per time window*, not just per subsystem

The same key can warrant different durability at different moments. An auction tolerates ~1s RPO (async cross-region replication) for most of its 7-day life, but in the **last 30 seconds** a lost bid is unrecoverable and decides the winner — so flip *that window* to **synchronous / quorum cross-region writes** and eat the latency only then. Consistency and durability are **local dials**, not global constants: pay the expensive posture where and when the stakes justify it.

### AZ loss ≠ region loss (don't over-build the cheap failure)

Scope the failure before designing the failover. **In-region replication** (e.g. Kafka RF=3 across AZs with `min.insync.replicas`, or a Multi-AZ DB standby) already survives an **AZ** outage with RPO≈0 and no extra work — that case is *free*. Only **region** loss needs cross-region machinery, and that's where the real RPO tradeoff lives (async ≈ seconds of loss; synchronous ≈ zero loss at a latency cost). A common mistake is engineering an elaborate active/active story for the AZ case that replication already handles — and worse, active/active *writers* reintroduce split-brain (two logs, two truths) unless one region owns the authoritative log.

## Techniques that push RPO toward zero

- **Event sourcing** — the source of truth is an append-only event log; snapshots/blobs are *materialized views* rebuilt from it. Persisting one row per event drops RPO from "one whole operation" to "one event." A coarse "one blob written at completion" sets RPO = one entire operation — often unacceptable for a long-running task.
- **Write-ahead logging (WAL) / durable execution** — record *intent* before the side effect, and *completion* after. On resume, an operation not marked `Completed` is in a known-uncertain state.

## The non-idempotent replay hazard

The subtle, dangerous part: **side effects are not transactional with your database.** If you `git push` (or send an email, or POST to a service) and crash *before* the completion row commits, naive "replay from the log" fires the effect **twice**. Most real effects aren't idempotent.

The contract that makes crashes safe:
1. **Write-ahead the intent** (state `Waiting`: "about to run T, input I, id C").
2. Execute the effect.
3. **Write the result** (state `Completed`).
4. **On resume, do NOT auto-replay a non-`Completed` operation.** `Waiting` means "may have executed, outcome unknown" → reconcile: surface to the user, or check external state — never blindly re-run.
5. **Classify operations**: pure / read-only (replay-safe) vs. side-effecting (reconcile). The classification is the design.

## Key points
- RPO ≠ RTO — how much lost vs. how long down; a system needs a target for each.
- **Durability of the log ≠ safety of replay** — you can perfectly persist history and still corrupt the world by replaying non-idempotent effects.
- **Idempotency is what makes retry/replay safe** — design effects to be idempotent (idempotency keys, conditional writes) where you can; reconcile where you can't.
- Persisting a materialized snapshot is an *optimization* over the event log, not the source of truth.
- **RPO is a local dial** — set it per subsystem *and* per time-window (e.g. tighten it only for an auction's final seconds); and scope the failure (AZ loss is free via in-region replication, only region loss needs cross-region).
- **The client is never an authoritative durability source** — client-side buffers/replay are a best-effort *hint* to narrow the RPO gap, not truth; authoritative records must be server-signed (a malicious client would forge values exactly during failover).

## Interview angle

> "RPO is how much data I can lose; RTO is how long I'm down. I set RPO per subsystem — RPO≈0 for irreplaceable state like a transcript, don't-care for a rebuildable index. To hit RPO≈0 I event-source: append each event fsync'd, snapshots are materialized views. The trap is non-idempotent side effects — they aren't transactional with the DB, so I write-ahead the intent, and on resume I never auto-replay a non-completed operation or I'll git push twice. Pure operations are replay-safe; side-effecting ones must be reconciled."

## Connections
- [[system-design-concepts/agent-loop]] — persisting each loop step (WAL of tool intent/result) is what makes a turn resumable
- [[tech/aws-rds-postgresql]] — RPO≈0 made concrete: synchronous WAL fsync + Multi-AZ standby; `synchronous_commit` is the RPO-vs-latency knob
- [[tech/aws-elasticache-redis]] — the RPO>0 case: async replication + snapshot interval, AOF off under Multi-AZ — "persisted" ≠ durable
- [[system-design-concepts/rds-vs-key-value-store]] — the durability contract is what separates a system of record from a cache
- [[theory/copy-on-write-vs-mvcc]] — the complementary consistency question: isolating concurrent writers vs. surviving a crash
- [[theory/consistent-hashing]] — both are foundational distributed-systems primitives for stateful fleets
- [[system-design-concepts/work-distribution]] — lease/checkpoint recovery when a worker dies mid-task is the same replay-safety problem
- [[system-design-concepts/read-state-watermarking]] — commit-before-ACK + client retry timer is RPO≈0 reasoning applied to chat message delivery
- [[system-design-concepts/hot-key-write-contention]] — the "sync only in the final window" dial is how you bound cost on a contended key without paying it always
- [[theory/consistency-models]] — RPO ("how much can I lose") is orthogonal to the consistency contract ("what order can I observe"); both are dials

## Sources
- [[sources/docs/local-coding-agent-system-design]] — §8 crash recovery, §9 durability glossary
- [local-coding-agent-system-design.md](https://github.com/redblackcoder/interview-prep-raw/blob/main/docs/local-coding-agent-system-design.md) — full mock-interview design notes
- [[sources/docs/design-instagram-auction-mock-interview]] — RPO as a per-window dial (sync writes for an auction's last 30s); AZ-vs-region scoping; client-replay is a hint, not truth
