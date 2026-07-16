---
source: docs/functional-programming-elm-study-guide.md
source_url: https://github.com/redblackcoder/interview-prep-raw/blob/main/docs/functional-programming-elm-study-guide.md
type: doc
date_extracted: 2026-07-01
topic: theory
---

# Functional Programming & Elm: Recursion, State, and Folds

## Key Ideas
- **Pure FP rests on three tenets:** pure functions (output determined solely by input, no side effects → deterministic + testable), immutability (no in-place mutation; "change" = shallow copy with new values), and expressions-over-statements (every construct evaluates to a value → in Elm, `else` is mandatory because `if/then/else` must yield a value).
- **The Elm Architecture (TEA)** is a closed unidirectional loop mediated by the **runtime**, not by direct calls: `Model → view(Model) → virtual DOM tagged with Msg → user event → runtime matches Msg → update(Msg, currentModel) → new Model → loop`. The View **cannot** call Update directly; the runtime brokers every transition and does the vDOM diff.
- **`foldl` vs `foldr` is fundamentally about evaluation order and the stack:** `foldl` combines on the way *down* (outermost action is the recursive call → tail-recursive → O(1) stack, TCO'd to a loop); `foldr` defers combining until the way *back up* (outermost action is the combining fn → not tail-recursive → O(N) stack → can overflow on huge lists).
- **Tail Call Optimization (TCO):** if the recursive call is the *absolute last* step, the compiler keeps no deferred context and reuses the stack frame, compiling recursion into an iterative loop. This is *why* `foldl` is the safe default for large lists.
- **Elm ergonomics that make folds readable:** the forward pipe `|>` (feeds LHS as the *final* argument to RHS, turning inside-out nesting into top-to-bottom pipelines), lambdas (`\item acc -> ...`), and auto-generated record accessors (`.weight` is a function `Clue -> Int`). `List.map .weight` and `List.foldl (+) 0` compose cleanly because of these.
- **Recurring solution ladder** across all three sample problems: **naive recursion → manual tail-recursive accumulator → idiomatic fold**, with a further step for the path-builder: **pattern-match the base case out of the loop** to eliminate a per-iteration `if` guard (seed accumulator from `first`, fold over `rest`).

## My Understanding
*(Seeded from the study guide; revise in your own words on a re-extract.)*
- The through-line I take away: **an accumulator threaded through a tail-recursive pass IS a fold**, and a fold with the right combining function IS a loop. Naive-recursion → manual-accumulator → `foldl` is the same computation at three altitudes of abstraction, and the reason to climb it is both readability *and* the O(N²)/stack pitfalls the naive versions hide.
- `foldl` vs `foldr` finally sticks when framed as "where does the real work happen": `foldl` does it going down (nothing to remember → constant stack), `foldr` defers it going up (must remember every frame → linear stack). "Which fold?" is really "can I afford O(N) stack, and do I need to see the tail before the head?"
- TEA clicks as the same **queue-and-runtime indirection** I keep meeting elsewhere: components emit *intent* (a `Msg`), a runtime owns the actual state transition. View→Update never calls directly, exactly like an event loop — decoupling that makes the whole thing testable because `update` is just a pure `(Msg, Model) -> Model`.

## Open Questions
*(Candidates to revisit.)*
- Elm `foldl`'s signature is `(a -> b -> b)` — element-first, accumulator-second — which is the *opposite* argument order from Haskell's `foldl (a -> b -> a)`. Worth internalizing so muscle memory from one doesn't break the other.
- Where do side effects actually go in TEA? (`Cmd` / `Sub` and the managed-effects boundary weren't covered here — the loop as described is pure state; how do HTTP/time/random plug in?)
- `foldr` can be lazy/short-circuiting in a lazy language (Haskell) but Elm is strict — does Elm's `foldr` gain anything over `foldl` besides output order, or is it strictly "use `foldl` unless you need right-to-left"?

## Connections
- Relates to: [[theory/pure-functional-programming]] — the three tenets (page this extract seeds)
- Relates to: [[theory/folds-and-tail-recursion]] — foldl/foldr mechanics + TCO (page this extract seeds)
- Relates to: [[tech/elm]] — TEA loop + syntax features (page this extract seeds)
- Relates to: [[coding-patterns/fold-accumulator]] — the naive→accumulator→fold ladder as a reusable pattern (page this extract seeds)

## Key Quotes / Annotations
Fold type signature (note element-first arg order):
> `foldl : (a -> b -> b) -> b -> List a -> b`

The foldl/foldr contrast (from the guide's table):
> foldl — outermost action is the recursive call; math evaluated immediately on the way down; O(1) stack; tail-recursive.
> foldr — outermost action is the combining function; math deferred, calculated on the way back up; O(N) stack; not tail-recursive.

Idiomatic reversal via fold + cons:
> `reverseListFold xs = List.foldl (::) [] xs`

Pattern-match to kill the per-iteration guard (Problem 3C):
> `first :: rest -> List.foldl (\item acc -> acc ++ " -> " ++ item) first rest`
