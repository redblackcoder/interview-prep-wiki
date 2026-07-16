# Elm

A pure functional language that compiles to JavaScript for building web front-ends. It enforces the [[theory/pure-functional-programming]] tenets at the language level (no runtime exceptions, enforced immutability, mandatory `else`) and ships with a prescribed application architecture — TEA.

## The Elm Architecture (TEA)

TEA is a **unidirectional data-flow loop mediated by the runtime**. Crucially, the View **cannot** call Update directly — the Elm runtime brokers every state transition in a closed loop:

```
   ┌──────────────────────────────────────────────┐
   │                                                │
   ▼                                                │
 Model ──▶ view(Model) ──▶ virtual DOM tagged      │
                            with Msg (e.g.          │
                            onClick Increment)      │
                                  │                 │
                          user interaction          │
                                  │                 │
                          runtime intercepts        │
                          native DOM event,         │
                          matches the Msg tag       │
                                  │                 │
                                  ▼                 │
                     update(Msg, currentModel)      │
                                  │                 │
                          returns a new Model ──────┘
                     (runtime stores it, re-runs view,
                      diffs the vDOM, patches the screen)
```

1. **Initial Model** — the program boots with a starting state.
2. **View** — the runtime feeds the Model to `view`, which returns a virtual-DOM blueprint whose event handlers are tagged with semantic messages (`Msg`).
3. **Interaction** — a user action fires a native DOM event; the runtime intercepts it and maps it to its `Msg`.
4. **Update** — the runtime calls `update` with the captured `Msg` and the *current* Model; `update` returns a brand-new Model.
5. **Loop closes** — the runtime stores the new Model, re-runs `view`, diffs the virtual DOM, and efficiently patches the real screen.

Because `update : Msg -> Model -> Model` is a pure function, the entire state logic is testable in isolation — no browser needed. The View→Update indirection (emit *intent*, let a runtime own the transition) is the same decoupling you see in event loops and message queues.

## Syntax and features that make functional code read well

### `let ... in` — local, immutable bindings
Declares constants, intermediate values, or helper functions scoped only to the expression after `in`:
```elm
let
    x = 10
    y = 20
in
    x + y
```

### Forward pipe `|>` — linear pipelines
Passes the left-hand result as the **final argument** to the right-hand function, converting inside-out nesting into top-to-bottom flow:
```elm
-- inside-out (hard to read):
cleanText = String.trim (String.toLower rawInput)

-- pipelined (readable):
cleanText =
    rawInput
        |> String.toLower
        |> String.trim
```

### Lambdas — anonymous functions
Backslash `\` (evoking λ), then params, `->`, body:
```elm
\item acc -> item :: acc
```

### Record field accessors — `.field` is a function
Elm auto-generates an accessor function for every record field, so `.weight` *is* a function `Clue -> Int`:
```elm
clue = { description = "Footprint", weight = 5 }
.weight clue        -- 5
List.map .weight clues   -- accessor used directly as a mapping function
```

These three combine to make fold pipelines terse: `clues |> List.map .weight |> List.foldl (+) 0`.

## Interview angle

> "Elm is a pure functional language for the front-end. Its architecture, TEA, is a runtime-mediated unidirectional loop: the view renders the model into virtual DOM tagged with messages, the runtime catches a user event, calls `update(msg, model)` to get a new model, then re-renders and diffs. The view never calls update directly — that indirection makes `update` a pure `(Msg, Model) -> Model` you can test without a browser. Syntactically, `|>` pipelines, lambdas, and `.field` accessor functions keep the functional style readable."

## Connections
- [[theory/pure-functional-programming]] — Elm enforces the three tenets; the runtime is where effects are quarantined so functions stay pure
- [[theory/folds-and-tail-recursion]] — `|>`, lambdas, and `.field` accessors are what make fold expressions concise in Elm
- [[coding-patterns/fold-accumulator]] — worked Elm examples of the fold/accumulator ladder

## Sources
- [[sources/docs/functional-programming-elm-study-guide]] — §2 The Elm Architecture (TEA); §3 Elm Syntax and Features
- Runnable example — a complete `Browser.sandbox` TEA program (Model/Msg/update/view) that reverses a comma-separated list: [Ellie sandbox](https://ellie-app.com/zkxTj4t9Yqwa1) · [code in raw](https://github.com/redblackcoder/interview-prep-raw/blob/main/code/elm-list-reversal/)
