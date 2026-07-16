# Fold / Accumulator Pattern

The workhorse pattern for reducing a list to a value in functional code: thread an **accumulator** through a single pass instead of mutating a running variable. Most list-processing problems climb the same ladder of abstraction — and recognizing which rung you're on (and which rung is idiomatic) is the whole skill.

## The abstraction ladder

For "reduce a list to one value" problems, solutions typically come in three rungs:

1. **Naive recursion** — recurse on the tail, then combine *after* the call returns. Often correct but hides pitfalls: it's usually *not* tail-recursive (O(N) stack), and if the combine step is itself O(N) (like `++`), the whole thing goes O(N²).
2. **Manual tail-recursive accumulator** — a local helper carries an accumulator; the recursive call is the last step, so it's O(1) stack and TCO'd to a loop. Fast and explicit, but verbose.
3. **Idiomatic fold** — recognize that rung 2 *is* a fold, and pass the combining function straight to `List.foldl`. Shortest and clearest, once you see it.

See [[theory/folds-and-tail-recursion]] for *why* rung 2/3 beat rung 1 on stack and time.

## Recognizing the rungs (Elm examples)

**List reversal** — the naive `rec xs_ ++ [x]` is O(N²) (append re-walks the list each step); the accumulator version prepends with the O(1) cons `::` for a single O(N) pass; the idiomatic version is just the cons operator handed to `foldl`:
```elm
List.foldl (::) [] xs
```

**Sum a field over records** — three equivalent shapes, trading passes for clarity:
- *map-then-fold* (`let weights = List.map .weight clues in List.foldl (+) 0 weights`) — two passes, a temp binding
- *pipelined* (`clues |> List.map .weight |> List.foldl (+) 0`) — two passes, no temp
- *single-pass fold* (`List.foldl (\clue acc -> clue.weight + acc) 0 clues`) — one pass, does the lookup and add together

The map→fold→pipeline→single-pass progression is the same computation optimized for either readability (pipeline) or performance (single pass).

## Sub-pattern: pattern-match the base case out of the loop

When a reduction needs *different behavior on the first element* (e.g. a separator-joined path where the first item has no leading separator), the naive move is an `if` guard *inside* the fold that runs every iteration:

```elm
-- guard evaluated on every element:
List.foldl (\item acc -> if acc == "" then item else acc ++ " -> " ++ item) "" nodes
```

The cleaner functional move is to **destructure the base case first**, then fold over the rest with no per-step branch:

```elm
case nodes of
    [] -> ""
    first :: rest -> List.foldl (\item acc -> acc ++ " -> " ++ item) first rest
```

This is a recurring FP instinct: **push conditional logic to the structure (pattern match) instead of the runtime (branch inside the loop).** The empty-list and non-empty cases become distinct, total branches, and the hot loop is guard-free.

## When to use

- Any "reduce a collection to a value" task: sum/product, min/max, count, build-a-string, group, flatten.
- Reach for **`foldl`** by default (O(1) stack); use `foldr` only when you need right-to-left order.
- Prefer the **single-pass fold** when the list is large or the map step is expensive; prefer the **pipeline** (`|> map |> fold`) when clarity matters more than the extra traversal.
- When the first (or last) element is special, **pattern-match it out** rather than branching inside the combiner.

## Interview angle

> "Most list-reduction problems have three forms: naive recursion, a manual tail-recursive accumulator, and an idiomatic fold — and they're the same computation at rising abstraction. I default to `foldl` for constant stack. If the first element needs special handling, I pattern-match it out as the initial accumulator instead of putting an `if` inside the fold — pushing the conditional to the structure keeps the loop branch-free."

## Connections
- [[theory/folds-and-tail-recursion]] — the mechanics (foldl/foldr, TCO) that justify climbing the ladder
- [[theory/pure-functional-programming]] — immutability is *why* you thread an accumulator rather than mutate a running total
- [[tech/elm]] — the syntax (`|>`, lambdas, `.field`) the examples are written in

## Sources
- [[sources/docs/functional-programming-elm-study-guide]] — §5 Sample Problems and Comparative Solutions
- Worked solutions in raw (pattern extracted here; full code there):
  - [Problem 1: List Reversal](https://github.com/redblackcoder/interview-prep-raw/blob/main/docs/functional-programming-elm-study-guide.md#problem-1-list-reversal) — naive O(N²) → tail-recursive accumulator → `foldl (::)`. Runnable TEA app: [Ellie sandbox](https://ellie-app.com/zkxTj4t9Yqwa1) · [code](https://github.com/redblackcoder/interview-prep-raw/blob/main/code/elm-list-reversal/)
  - [Problem 2: Guilt Calculator](https://github.com/redblackcoder/interview-prep-raw/blob/main/docs/functional-programming-elm-study-guide.md#problem-2-guilt-calculator) — map-then-fold → pipeline → single-pass fold
  - [Problem 3: Path/Breadcrumb Builder](https://github.com/redblackcoder/interview-prep-raw/blob/main/docs/functional-programming-elm-study-guide.md#problem-3-pathbreadcrumb-builder) — the pattern-match-out-the-base-case sub-pattern
