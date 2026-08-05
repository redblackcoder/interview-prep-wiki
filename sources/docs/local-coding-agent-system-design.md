---
source: docs/local-coding-agent-system-design.md
source_url: https://github.com/redblackcoder/interview-prep-raw/blob/main/docs/local-coding-agent-system-design.md
type: doc
date_extracted: 2026-08-04
topic: system-design-concepts
---

# Local Coding Agent — System Design

Distilled from an L7 mock interview: "Design an AI conversation chatbot," scoped to a **local, terminal-based coding agent** over a ~100 GB codebase with thin cloud deps (auth, LLM gateway).

## Key Ideas

- **The central bridge is the design.** 100 GB workspace → <1M-token window → <200K *useful* tokens/turn. The retrieval subsystem exists to span ~4 orders of magnitude. Eagerly embedding ~6B tokens is infeasible (hours–days, constant churn) — this kills "vector DB over the whole codebase."
- **Context assembly = a cost-tiered retrieval ladder, cheapest-first**, that the model navigates like a new engineer: (1) free structural priors (tree, manifests, README, CLAUDE.md), (2) symbol index (ctags → tree-sitter → LSP), (3) grep for literal anchors, (4) lazy embeddings of a hot subset only. Each rung is more expensive and more semantically powerful; climb only as far as the query forces.
- **Pull-based retrieval** (model pulls via search tools) over push-based (agent pre-injects). Its blind spot: grep needs a literal anchor, so it degrades on *semantic* queries — covered by the symbol + embedding tiers.
- **Permission ≠ isolation.** An approval model (auto-allow / ask / deny) answers *should this run?*; sandboxing answers *what can it touch?* You need both.
- **Model judgment is never a security boundary.** Pull-based reads ingest repo files that can carry prompt injection → auto-approved `bash` → RCE. Assume the model is compromised; enforce **containment over policy** (read-only mounts + `EPERM`, network egress control, process isolation) at the OS boundary rather than parsing command arguments.
- **Two storage subsystems, two consistency models** — the core L7 judgment: transcript = durable, strongly consistent, single-writer-per-session, **RPO≈0**; index = rebuildable, eventually-consistent, shared cache. Each earns its posture.
- **Crash recovery's hard part: tool side effects aren't transactional with the DB.** Write-ahead the intent, don't auto-replay non-`Completed` turns (non-idempotent tools like `git push` fire twice), classify tools replay-safe vs. reconcile, and event-source the turn to drive RPO from "one turn" to "one event."
- **Session isolation = copy-on-write, not MVCC.** Base index keyed by commit SHA (shared free via git object store) + per-session overlay of changed files. Maps 1:1 onto **git worktrees** (physical isolation). MVCC's guarantees are overkill for a rebuildable cache.
- **The agent loop is a reactive state machine, not a planner.** Sequence by reacting across turns (edit → see it apply → test → see failure → fix), because the environment (compile/test output) is ground truth. Verification is *just more tool calls*, needing a max-iteration/cost circuit-breaker to guarantee convergence.

## My Understanding

- The single biggest realization: the hard problems kept getting **relabeled into easier neighbors** — "context assembly" quietly became "context-window fitting" (truncation/compaction), and "tool sandboxing" quietly became "the permission model." Fitting and permissions are the comfortable cousins; assembly (what goes in the box) and containment (what a running tool can touch) are the actual cores. Naming the hard part explicitly and checking I'm solving *it* is the discipline.
- The two cores **intersect**: pull-based retrieval is exactly the ingestion vector for the prompt injection that containment must stop. Seeing that connection — rather than treating retrieval and safety as independent — is the systems-thinking signal.
- Keeping the agent "dumb" and pushing intelligence to the model is a real architectural commitment: the agent owns the **contract, the tools, and the bootstrap map**, not the intelligence to climb it. Delegation is fine; delegation *without specifying the contract at the boundary* reads as dodging.
- Latency can't be one number — total task time is emergent and mostly not mine. Decompose the turn by **ownership** (mine / provider / external), SLO only what I own + responsiveness, and make everything **attributable** via telemetry.

## Interview-Craft Lessons

- **Lead, time-box, checkpoint** — 3-min depth → checkpoint → hand back. The pause is the senior move; continuous talking reads as anxious. Deferring to the interviewer's *steer* is compatible with leading the *default path*.
- **Prioritization filters:** The Bridge (orders-of-magnitude span), The Hot Path (runs every turn), The Blast Radius (data loss/breach/silent-wrong), The Fingerprint (commodity → name and move). First three = load-bearing; fourth = what to drop.
- **Three buckets for unknowns:** Ask (forks the design) / State & proceed (mechanism detail → design to an interface) / Can't assume past (the thing being tested). Design to interfaces, never bluff — calibrated uncertainty builds credibility.
- **Recurring anti-patterns to kill:** (1) relabeling the core into an easier neighbor; (2) bolting on bespoke mechanism when a general primitive suffices ("structured output" channel, "verification section"); (3) delegating across a boundary without stating the contract.

## Open Questions

- **Unit of permission** — tool vs. argument. `git status` and `git push --force` are the same tool; how do you express a rule over arbitrary command strings without the pattern-matching trap? (Answer leans on containment, but the approval-list granularity is still unresolved.)
- **Egress policy granularity** — what exactly is allowed out, and what breaks when the network is cut mid-`npm install`?
- **Worktree disk ceiling** — 20 worktree checkouts of a 100 GB repo blow up disk (object store shared, checkouts not). Reflink clones (APFS/btrfs)? Only editing sessions get worktrees?
- **Detached-tool side effects** — an orphaned `npm test` after cancellation keeps mutating the workspace while its result is marked CANCELLED; how to reconcile the divergence between model world-model and filesystem?
- **Model↔agent wire contract** — assumed typed content blocks + co-emission = parallel; exact provider shapes to confirm (design is robust to the range via the adapter boundary).

## Model↔Agent Contract (assumed interface)

- Typed content blocks: `text` (display) + `tool_use` (id, name, JSON args). No stream string-parsing; no separate "structured output" channel.
- Multiple `tool_use` in one message = parallelizable (co-emission is the convention). Sequential = across turns (dependency expressed by waiting → costs round-trips).
- Execute after the message completes (stop_reason: tool_use), not mid-stream.
- Message array: stable `system` prefix (agent prompt + tool defs + CLAUDE.md, prompt-cached) + appended `messages`. Compaction touches history only, preserving the cached prefix.

## Durability Glossary

- **RPO** (Recovery Point Objective): max data lost on failure. RPO≈0 = lose nothing (synchronous fsync/WAL before ack); bought with write-path latency.
- **RTO** (Recovery Time Objective): how long recovery takes. RPO = how much lost; RTO = how long down.
- **Event sourcing:** append-only event log as source of truth; snapshots are materialized views. Drops RPO from "one turn" to "one event."
- **Write-ahead logging / durable execution:** record intent before the effect, completion after — so non-idempotent effects aren't blindly replayed on resume.
- **MVCC vs COW:** MVCC = snapshot-isolation guarantees (version chains, GC), overkill for a rebuildable cache. COW overlay = base + per-consumer deltas; isolation is free; maps onto git worktrees.

## Connections

- Relates to: [[system-design-concepts/consistent-hashing]] — content-hash keying of index/embeddings echoes stable-assignment-by-hash
- Relates to: [[theory/bloom-filters]] — probabilistic/space-efficient membership, adjacent to dedup/caching concerns
- Relates to: [[system-design-concepts/network-security-layers]] — "payload vs envelope"; here, model judgment ≠ security boundary, containment at the OS layer
- Relates to: [[system-design-concepts/work-distribution]] — parallel sessions, shared index vs. per-session isolation
- Relates to: [[tech/https-tls]] — TLS/egress as the transport for confidential context leaving the machine

## Key Quotes / Annotations

Framing that scored (say out loud):
> "The transcript is durable, strongly consistent, single-writer-per-session, RPO≈0 — losing history is unacceptable. The index is a rebuildable, eventually-consistent, shared cache — staleness is cheap to repair."

> "I assume the model is compromised by injection and design the sandbox so that assumption is survivable."

> "The model navigates the repo like a new engineer dropped into it — read the tree, skim the README/CLAUDE.md, grep a symbol, open a file. My job is to provide that ladder, not the intelligence to climb it."
