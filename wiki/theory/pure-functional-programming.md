# Pure Functional Programming

A paradigm that treats computation as the evaluation of mathematical functions, rather than as a sequence of commands that mutate global state. Where imperative code says *how* to change things step by step, functional code describes *what* a value is.

## The three tenets

1. **Pure functions** — a function's return value is determined *solely* by its input arguments, with no observable side effects (no mutating globals, no console/network I/O, no in-place mutation). The same inputs always produce the same output, which makes the code deterministic and trivially testable (no setup/teardown, no mocking of hidden state).
2. **Immutability** — once a data structure is created it cannot be modified. "Changing" a record means creating a (shallow) copy with the updated fields. This eliminates an entire class of bugs rooted in shared mutable state — aliasing, race conditions, spooky action at a distance.
3. **Expressions over statements** — every control-flow construct evaluates to a value. In Elm, `if/then/else` is not a statement that conditionally executes blocks; it is an *expression* that must return a value — which is why the `else` branch is **mandatory** (an expression with no value in one branch is meaningless).

## Why it matters

- **Testability & reasoning**: a pure function is a closed box — its behavior is fully captured by its type signature and body. You can reason about it locally, without tracing the whole program's state.
- **Referential transparency**: any call can be replaced by its result without changing meaning. This is what enables aggressive compiler optimization, memoization, and safe refactoring.
- **Concurrency**: immutability means no data races — there's no shared mutable state to guard with locks.
- **The cost is managed, not eliminated**: real programs need effects (I/O, state). Functional systems don't ban effects; they push them to a controlled boundary (e.g. Elm's runtime and `Cmd`/`Sub`), keeping the core logic pure. See [[tech/elm]].

## Interview angle

> "Pure FP rests on three ideas: functions whose output depends only on their inputs, data that's never mutated in place, and control flow that's expressions returning values rather than statements. The payoff is determinism — a pure function is fully described by its signature, so it's testable and safe to refactor, and immutability kills shared-state bugs and data races. Effects don't disappear; they get pushed to a managed boundary so the core stays pure."

## Connections
- [[tech/elm]] — a language that enforces all three tenets; the runtime is where effects are quarantined so functions stay pure
- [[theory/folds-and-tail-recursion]] — recursion + accumulators replace mutable loop counters; the functional way to iterate
- [[coding-patterns/fold-accumulator]] — immutability is *why* you thread an accumulator instead of mutating a running total

## Sources
- [[sources/docs/functional-programming-elm-study-guide]] — §1 Pure Functional Programming Concepts
