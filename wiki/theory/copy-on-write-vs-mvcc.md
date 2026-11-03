# Copy-on-Write vs MVCC: Isolating Concurrent Views

When many readers/writers need their *own* view of shared state without stepping on each other, two families of technique come up. They are often conflated; picking the wrong mental model leads to over-engineering.

## MVCC (Multi-Version Concurrency Control)

Databases (Postgres, InnoDB) keep **multiple versions** of each row. A transaction reads a consistent **snapshot** as of its start; writers create new versions rather than overwriting, so readers never block writers and never see dirty data. The machinery it *buys* you:

- Snapshot isolation / consistency guarantees (no dirty/non-repeatable reads).
- Version chains per row + visibility rules per transaction.
- **Garbage collection** of dead versions (Postgres `VACUUM`).

That machinery is the point — and the cost. You want MVCC when you need **transactional isolation guarantees** over authoritative, mutable state.

## Copy-on-Write (COW) overlay

A **shared immutable base + per-consumer delta** of only what changed; a read merges overlay-over-base. Examples: OverlayFS, Docker image layers, filesystem snapshots (APFS/btrfs), and **git itself**.

- No version chains, no GC, no visibility protocol.
- Isolation is *free*: consumer X simply never merges consumer Y's overlay.
- Ideal for **rebuildable / stale-tolerant** state where you don't need transactional guarantees, just *separation*.

## Choosing between them

> If the state is authoritative and needs isolation *guarantees* → MVCC. If it's a rebuildable cache and you only need *separate views* → COW overlay. Reaching for MVCC on a throwaway cache makes you build version chains and GC you never needed.

**Worked example — a per-workspace code index serving many agent sessions** (see [[system-design-concepts/context-assembly-retrieval-ladder]]):
- **Base index**, immutable, **keyed by commit SHA** — shared free via git's object store.
- **Per-session overlay** = only that session's changed files, re-parsed incrementally.
- A read = overlay-over-base; session X never sees session Y's edits. Isolation by construction.
- This maps **1:1 onto git worktrees**: each active session gets its own working directory sharing one `.git` object store — COW at the git layer, giving *physical* isolation (also a [[system-design-concepts/agent-tool-sandboxing|filesystem-isolation]] win). The index is [[theory/durability-rpo-rto|rebuildable]], so eventual consistency is fine — no MVCC needed.
- **Ceiling to name**: N worktree *checkouts* of a 100 GB repo blow up disk (object store shared, checkouts not) → give only *editing* sessions worktrees, or use reflink clones (APFS/btrfs) for cheap COW checkouts.

## Key points
- **COW = base + delta; MVCC = versions + snapshots + GC.** Related idea (don't mutate in place), different guarantees.
- **Match the tool to the state's authority**: authoritative+isolation → MVCC; rebuildable+separation → COW.
- `git` is a COW content-addressed store — commit SHAs are the shared immutable base, branches/worktrees are overlays.
- **mtime lies on `git checkout`** — key caches/overlays by content hash, not modification time.

## Interview angle

> "MVCC and copy-on-write both give isolated views without overwriting, but they buy different things. MVCC gives transactional snapshot-isolation guarantees — version chains, visibility rules, GC — which you want over authoritative mutable data. COW is just a shared immutable base plus a per-consumer delta; isolation is free but there are no transactional guarantees. For a rebuildable per-workspace code index shared by many sessions, I'd use COW keyed by commit SHA with per-session overlays — which maps straight onto git worktrees for physical isolation. Reaching for MVCC there would over-build."

## Connections
- [[system-design-concepts/context-assembly-retrieval-ladder]] — the per-session index overlay that keeps retrieval correct across branches
- [[theory/durability-rpo-rto]] — the complementary axis: isolation of concurrent views (this) vs. surviving a crash (that)
- [[system-design-concepts/agent-tool-sandboxing]] — per-session git worktrees also give physical filesystem isolation between agents
- [[theory/consistent-hashing]] — another core primitive for building stateful, sharded systems

## Sources
- [[sources/docs/local-coding-agent-system-design]] — §8 session state, scale & durability
- [local-coding-agent-system-design.md](https://github.com/redblackcoder/interview-prep-raw/blob/main/docs/local-coding-agent-system-design.md) — full mock-interview design notes
