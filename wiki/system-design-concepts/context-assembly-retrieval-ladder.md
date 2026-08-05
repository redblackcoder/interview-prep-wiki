# Context Assembly: The Retrieval Ladder

For any agent operating over a corpus far larger than its context window, the central design problem is **projection**: how do you pick the small set of tokens that actually matter from a vast source? A local coding agent makes this stark — a workspace can be **~100 GB (~6B tokens)** but the model call is capped at **<1M tokens, realistically <200K *useful* tokens/turn**. The subsystem that bridges that ~4-orders-of-magnitude gap *is* the design; everything else hangs off it.

The naive move — embed the whole corpus into a vector DB — **fails**: embedding ~6B tokens is hours-to-days of compute, the codebase churns constantly, and most of it is irrelevant to any one task. Retrieval quality caps the whole agent: garbage context in → garbage output, regardless of model strength.

## Pull vs push (name the decision)

- **Push-based**: the agent pre-retrieves relevant code and injects it. Front-loads work into one call; needs the agent to be "smart."
- **Pull-based**: the model pulls context on demand via search *tools* (grep, read, symbol lookup). Keeps the agent dumb, is more precise, trades tokens + round-trips.

Pull-based is the usual choice — but own its **blind spot**: `grep` needs a *literal anchor*. It degrades on *semantic* queries ("where do we handle idempotency?" when the code says `retry`/`dedup`), exactly when the codebase is unfamiliar. The symbol and embedding tiers below exist to cover that gap.

## The ladder — cheapest-first, climb only as far as the query forces

Each rung is more expensive **and** more semantically powerful. The model navigates it the way a new engineer navigates an unfamiliar repo.

1. **Free structural priors** (ms, no embeddings): directory tree (gitignore-aware, *lazy* — expose a `list_directory` tool, don't hardcode traversal depth), package manifests (`package.json`, `go.mod`, `BUILD`) for module boundaries/entry points, README, and **CLAUDE.md/AGENT.md** — the human-authored cold-start map.
2. **Symbol index** (deterministic, cheap, no embeddings): symbol → location.
   - **ctags** — flat tags file (name→file,line,kind); shallow (definitions, not references); builds in seconds.
   - **tree-sitter** — incremental, error-tolerant parser → per-file AST; re-parses only the changed subtree on edit. Per-file structure, no cross-file types.
   - **LSP** (gopls/pyright/rust-analyzer/tsserver) — running server, real semantic analysis, cross-file go-to-def/find-references; most powerful, heaviest (GBs RAM, warm-up).
   - Progression = the cost curve: ctags (lexical) → tree-sitter (syntactic, incremental) → LSP (semantic, cross-file, exact).
3. **Full-text grep** — the fastest path when there's an exact token (error string, config key, literal). A different tool for a different query shape, not strictly worse.
4. **Lazy embeddings of a hot subset** — only if 1–3 miss; semantic ranking *within* an already-small candidate set. A scalpel applied last, **never** a dragnet over the corpus.

## Embeddings, done right

- **Unit = semantic chunk (function/class/method)**, not a whole file (one vector per 5k-line file destroys precision). **tree-sitter's AST spans (tier 2) define the chunk boundaries** — the symbol layer *feeds* the embedding layer.
- **Trigger**: lazy — the narrowed candidate set, or the session's working set (files actually opened).
- **Cache key = content hash of the chunk** (not path/mtime — mtime lies on `git checkout`). Shareable across sessions, survives branch switches if the body is byte-identical. Changed chunk → new hash → stale entry ignored, re-embed on demand; LRU-evict.

## Key points
- The ladder is **cost-ordered and recall-ordered**: cheap/exact first, expensive/fuzzy last and scoped.
- **Background-warm the cheap deterministic tiers** (symbol index); keep embeddings lazy. Freshness via incremental re-parse + a git-checkout hook, *not* blind file-watching (`git checkout` flips thousands of files atomically). The index is a rebuildable, **stale-tolerant** cache → eventual consistency is fine.
- Pull-based retrieval is also the **ingestion vector for prompt injection** — see [[system-design-concepts/agent-tool-sandboxing]].

## Interview angle

> "The corpus is ~6B tokens; the window is <200K useful tokens. That gap is the design. I don't embed the whole thing — that's days of compute against constantly-churning code. I go pull-based: the model pulls context via tools, climbing a cost-tiered ladder — free structural priors and CLAUDE.md, then a symbol index (ctags/tree-sitter/LSP), then grep for literal anchors, and only lazily embed a hot subset when semantic search is unavoidable. The model navigates the repo like a new engineer; my job is to build the ladder, not the intelligence to climb it."

## Connections
- [[system-design-concepts/agent-loop]] — retrieval happens *inside* the loop; the model pulls context via tool calls each turn
- [[system-design-concepts/agent-tool-sandboxing]] — pull-based reads ingest repo files that can carry prompt injection; the two cores intersect here
- [[theory/consistent-hashing]] — content-hash keying of chunks echoes stable identity-by-hash
- [[theory/copy-on-write-vs-mvcc]] — a per-session index overlay keeps retrieval correct across branches/worktrees
- [[system-design-concepts/work-distribution]] — one shared per-workspace index serving many sessions is itself a fan-out problem

## Sources
- [[sources/docs/local-coding-agent-system-design]] — §6 Context assembly: the cost-tiered retrieval ladder
- [local-coding-agent-system-design.md](https://github.com/redblackcoder/interview-prep-raw/blob/main/docs/local-coding-agent-system-design.md) — full mock-interview design notes
