# Elm List Reversal Sandbox

A complete, runnable Elm (TEA) program that solves **Problem 1: List Reversal**
from the [Functional Programming & Elm study guide](../../references/functional-programming-elm-study-guide.md).
The user types a comma-separated list, presses a button, and sees it reversed.

## Live sandbox
- **Ellie:** https://ellie-app.com/zkxTj4t9Yqwa1

## Files
- `Main.elm` — the full program (Model / Msg / update / view / main via `Browser.sandbox`)
- `index.html` — the Ellie HTML host that boots `Elm.Main.init`

## What it demonstrates
- **The fold/accumulator pattern** — `reverseList` uses the idiomatic `List.foldl (::) [] xs`
  (rung 3 of the abstraction ladder). The naive O(N²) recursion is left commented inline
  for contrast.
- **The Elm Architecture (TEA)** end-to-end — `Browser.sandbox` wiring `initialModel`,
  `view` (input + button tagged with `UpdateInput` / `ReversePressed` msgs), and a pure
  `update : Msg -> Model -> Model`.
- **Pipeline parsing** — the `ReversePressed` branch cleans input with
  `String.split "," |> List.map String.trim |> List.filter (not << String.isEmpty)`
  inside a `let ... in` block.

## Note
The list here is `List String`, so `reverseList : List String -> List String` — the
values ("1", "2", "3") stay strings; they are split and rejoined as text, never parsed
to `Int`.
