# The Agent Loop: A Reactive State Machine

An LLM coding agent is not a planner that decides everything up front and executes — it is a **reactive loop** that acts, observes the environment's response (compile errors, test output, tool results), and decides the next step from that ground truth. Treating it as a planner is a recurring design mistake; the environment is the source of truth the model must react to at each step.

## The message array (the thing that actually matters)

```
system:   agent prompt + tool definitions + CLAUDE.md   ← stable prefix (prompt-cache this)
messages: [ history…, user msg,
            assistant(text + tool_use),                 ← model's turn
            user(tool_result),                          ← agent appends after executing
            assistant(tool_use), … ]                    ← loop continues
```

The **system block is a stable prefix → prompt-cache it** so you don't re-pay for it every iteration. The loop only *appends* to `messages`. The API is stateless; you rebuild the array each call (with provider-side caching to blunt the cost).

## Model↔agent contract (the interface to design against)

You don't need to know a specific provider's wire format if you design to a contract:

- The model emits **typed content blocks**: `text` (display to user) and `tool_use` (id, name, JSON args). You do **not** string-parse tool calls out of a text stream, and you don't need a bespoke "structured output" side-channel — the narration text *is* a separate block.
- **Multiple `tool_use` blocks in one message = parallelizable** (co-emission is the convention; there's no separate "parallel-safe" flag).
- **Sequential = across turns**: emit A → agent returns A's result → model emits B next turn. Dependency is expressed by *waiting*, which costs round-trips.
- **Execute after the message completes** (`stop_reason: tool_use`), not mid-stream — the model may still be deciding.

## The loop

assemble context → gateway → stream tokens → on `tool_use`, check permission ([[system-design-concepts/agent-tool-sandboxing|auto-allow vs ask]]) → execute with timeout → append `tool_result` (status ∈ {SUCCESS, FAIL, TIMEOUT, CANCELLED, DISAPPROVED}) → resend → repeat until no `tool_use` → apply edit (diff, per permission) → **verify** → terminate when applied *and green*.

**Verification is not a special phase** — it's ordinary tool calls inside the same loop: `edit → run_tests (tool_use) → failures return (tool_result) → fix → repeat`. Inventing a dedicated "verification section" re-adds intelligence to an agent you chose to keep dumb, and it's fragile: a pre-declared plan commits to running tests before it knows the edits applied cleanly. React across turns instead.

## Invariants & hazards
- **tool_use ↔ tool_result pairing**: every `tool_use` block must be answered by a `tool_result` in the next message. On interrupt after running 1 of 3 co-emitted calls, synthesize `CANCELLED` results for the other 2 or the next API call is malformed. Interrupt = "leave the message array valid," not just "stop."
- **Cancellation is a hard requirement** (not a non-goal): a code-executing agent must be killable at any point. Mid-stream *steering* is a reasonable non-goal; abort is not.
- **Cancellation granularity**: killing a subprocess mid-write leaves a torn workspace. Define the contract — interrupt between LLM calls vs. during a tool run.
- **Bounded tool output**: a single `grep`/`cat`/verbose test run can blow the window. Truncate **per-tool** (tail for build/test summaries, but *not* for grep's flat list) and inject an explicit `[… N lines omitted …]` marker — silent truncation makes the model reason on a partial result as if complete.
- **Convergence**: edit→test→fix can loop forever → needs a **max-iteration / cost circuit-breaker** on the "infinite" session.
- **Time-to-first-feedback** is the SLO that makes it feel alive (spinner/"reading files…" fast); total task time is emergent and mostly not yours — see the ownership-based latency model in the source.

## Interview angle

> "The agent is a reactive loop, not a planner. The model emits typed text and tool_use blocks; multiple tool_use in one message means parallel, sequential means across turns by waiting for results. I execute after the message completes, append tool_results, and resend — with the system prompt as a cached stable prefix. Every tool_use must be paired with a tool_result, so interrupt has to leave the array valid, not just stop the loop. Verification isn't a special phase — it's just more tool calls, edit→test→fix, with a circuit-breaker so it terminates."

## Connections
- [[system-design-concepts/context-assembly-retrieval-ladder]] — retrieval is the model pulling context via tool calls each iteration
- [[system-design-concepts/agent-tool-sandboxing]] — the permission/containment check sits on the execute-tool step
- [[theory/durability-rpo-rto]] — persisting each loop step is what makes a turn resumable after a crash
- [[system-design-concepts/work-distribution]] — many such loops run as parallel sessions on one machine

## Sources
- [[sources/docs/local-coding-agent-system-design]] — §4 One turn end-to-end, §5 latency, §8 verification & convergence
- [local-coding-agent-system-design.md](https://github.com/redblackcoder/interview-prep-raw/blob/main/docs/local-coding-agent-system-design.md) — full mock-interview design notes
