# Folds and Tail Recursion

A **fold** reduces a list to a single value by threading an *accumulator* through a combining function applied to each element. It is the functional replacement for an imperative loop with a running variable — and the direction you fold from determines both the result and the memory cost.

```elm
foldl : (a -> b -> b) -> b -> List a -> b
--       combine          init   input    result
```

## foldl vs foldr

The key distinction is *where the real work happens* relative to the recursion:

| Feature | `foldl` (left fold) | `foldr` (right fold) |
|---|---|---|
| Direction | left → right (start to end) | right → left (end to start) |
| Outermost action | the recursive **call** | the combining **function** |
| When math runs | immediately, on the way *down* | deferred, on the way *back up* |
| Stack space | **O(1)** — constant | **O(N)** — linear |
| Tail recursive? | **Yes** (TCO'd to a loop) | **No** (can stack-overflow on huge lists) |

- **`foldl` combines as it descends.** Each step has nothing left to remember, so there's no deferred context — the recursion is tail-recursive and runs in constant stack.
- **`foldr` combines as it unwinds.** It must reach the end of the list *before* the first combination can happen, so every pending frame stays on the stack — O(N), and a deep enough list overflows.

**Default to `foldl`** unless you specifically need right-to-left order (e.g. building a list while preserving original order, or a right-associative operation).

> ⚠️ Argument-order gotcha: Elm's `foldl` combining function is `(a -> b -> b)` — **element first, accumulator second**. This is the opposite of Haskell's `foldl` (`b -> a -> b`). Muscle memory from one language will mis-order the other.

## Tail Call Optimization (TCO)

A function is **tail recursive** when the recursive call is the *absolute last* step — nothing (no `+`, no `::`, no wrapping) happens to its result before returning.

```elm
-- Tail recursive: the call to reverseHelper is the last thing that happens
reverseHelper acc remaining =
    case remaining of
        [] -> acc
        head :: tail -> reverseHelper (head :: acc) tail
```

When the call is in tail position, the compiler knows the current frame's local context will never be needed again, so it **reuses the stack frame** instead of allocating a new one — compiling the recursion down to an iterative loop. This is what gives `foldl` its O(1) stack and makes it safe on arbitrarily long lists.

The classic *non*-tail-recursive shape, for contrast:

```elm
-- NOT tail recursive: must hold the frame to run ++ [x] AFTER the call returns
reverseListNaive xs =
    case xs of
        [] -> []
        x :: xs_ -> reverseListNaive xs_ ++ [x]   -- O(N) stack + O(N²) time
```

## Interview angle

> "A fold reduces a list through an accumulator — it's the functional `for` loop. `foldl` combines on the way down, so it's tail-recursive and runs in constant stack; `foldr` defers combining until it unwinds, so it costs O(N) stack and can overflow. Tail-call optimization is the enabler: when the recursive call is the last step, the compiler reuses the stack frame and turns recursion into a loop. Reach for `foldl` by default, `foldr` only when you need right-to-left."

## Connections
- [[coding-patterns/fold-accumulator]] — the practical pattern built on these mechanics (naive → accumulator → fold)
- [[theory/pure-functional-programming]] — folds/recursion are how you iterate without a mutable loop variable
- [[tech/elm]] — `|>`, lambdas, and `.field` accessors are what make fold expressions read cleanly

## Sources
- [[sources/docs/functional-programming-elm-study-guide]] — §4 Mechanics of Folds and Recursion; §5 Problem 1 (reversal) illustrates naive vs tail-recursive vs fold
