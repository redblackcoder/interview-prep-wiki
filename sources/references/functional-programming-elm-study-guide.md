# Functional Programming & Elm: Study Guide on Recursion, State, and Folds

This study guide captures the fundamental paradigms of pure functional programming, state routing within declarative web frameworks, the mechanics of list folding (left vs. right), tail call optimization (TCO), and architectural patterns. It serves as a comprehensive reference guide complete with comparative solution patterns to common algorithmic problems.

## 1. Pure Functional Programming Concepts

Unlike imperative programming, which focuses on sequence of commands and mutating global state, pure functional programming treats computation as the evaluation of mathematical functions. Three core tenets define this approach:

- **Pure Functions:** A function's return value is determined solely by its input arguments, without observable side effects (such as modifying global variables, performing console I/O, or mutating state in-place). This makes the code deterministic and highly testable.
- **Immutability:** Once a data structure is created, it cannot be modified. Changing a record's state requires creating a shallow copy with the updated values. This eliminates entire classes of bugs related to shared mutable state.
- **Expressions over Statements:** In Elm, every control flow construct is an expression that evaluates to a value. For example, if-then-else is not a statement that executes blocks of code conditionally; it is an expression that must return a value, making the else branch strictly mandatory.

## 2. The Elm Architecture (TEA) State Loop

The Elm Architecture (TEA) is a unidirectional data flow pattern. In Elm, the **View** cannot call the **Update** function directly. Instead, state transitions are mediated entirely by the **Elm Runtime Engine** in a closed-loop system:

1. **The Initial Model:** The program boots with an initial state configuration (the Model).
2. **Rendering (View):** The Elm Runtime feeds the Model into the view function. The view function returns a virtual DOM blueprint labeled with semantic messages (Msg) attached to UI event handlers (e.g., onClick Increment).
3. **User Interactivity:** When a user triggers an interaction, the browser registers a native DOM event. The Elm Runtime intercepts this event, matches it to the semantic Msg tag, and captures it.
4. **State Transition (Update):** The Runtime calls the update function, passing both the captured Msg and the **current** Model. The update function evaluates the transition and returns a brand-new Model.
5. **The Loop Closes:** The Runtime stores the new Model, runs the view function again with this updated state, performs a virtual DOM diff, and updates the real browser screen efficiently.

## 3. Elm Syntax and Features

### A. Let-In Expressions

The let...in construct allows the declaration of local constants, intermediate computations, or helper functions that are scoped exclusively to the expression immediately following the in keyword. These variables are immutable and private to the block.

```elm
let
    x = 10
    y = 20
in
    x + y
```

### B. The Forward Pipe Operator (|>)

The forward pipe operator passes the result of the left-hand expression as the **final argument** to the function on the right. This converts inside-out nested functions into linear, left-to-right (or top-to-bottom) data pipelines.

```elm
-- Nested, inside-out (Hard to read):
cleanText = String.trim (String.toLower rawInput)

-- Pipelined, top-to-bottom (Readable):
cleanText =
    rawInput
        |> String.toLower
        |> String.trim
```

### C. Anonymous Functions (Lambdas)

Lambdas are one-off, unnamed functions defined on the fly. The syntax begins with a backslash \ (mimicking the Greek lambda symbol), followed by parameter names, a right arrow ->, and the function body.

```elm
-- Anonymous lambda that takes two parameters:
\item acc -> item :: acc
```

### D. Record Field Accessor Functions

For any record containing a field, Elm automatically generates an accessor function prefixed with a dot (e.g., .weight). This is a shorthand function that takes a record and returns the value of that field.

```elm
-- Record:
clue = { description = "Footprint", weight = 5 }

-- Accessor:
.weight clue -- Evaluates to 5
```

## 4. The Mechanics of Folds and Recursion

Folding is a method of reducing a list of elements down to a single value by applying a combining function across an accumulator. The type signature for both left and right folds is:

```elm
foldl : (a -> b -> b) -> b -> List a -> b
```

| Feature | foldl (Left Fold) | foldr (Right Fold) |
| :---- | :---- | :---- |
| **Direction** | Left-to-Right (Start to End) | Right-to-Left (End to Start) |
| **Outermost Action** | The recursive function call | The combining function f |
| **Math Execution** | Evaluated immediately on the way down | Deferred; calculated on the way back up |
| **Memory Stack** | Constant stack space: O(1) | Linear stack space: O(N) |
| **Tail Recursive?** | **Yes (Tail Call Optimized)** | **No (Can Stack Overflow on huge lists)** |

### Tail Call Optimization (TCO)

A function is tail recursive if the recursive call is the absolute last execution step of the function. The compiler does not need to keep track of any local context or deferred mathematical operations. It can reuse the current stack frame instead of allocating a new one, compiling the recursion down to an ultra-efficient loop behind the scenes.

## 5. Sample Problems and Comparative Solutions

### Problem 1: List Reversal

**Goal:** Reverse a list of strings cleanly and efficiently.

#### Approach A: Naive Recursion (Slow, O(N^2) Time Complexity)

This approach is slow because appending elements with ++ requires traversing the entire left-hand list step-by-step. Repeating this recursively causes quadratic time execution.

```elm
reverseListNaive : List String -> List String
reverseListNaive xs =
    case xs of
        [] -> []
        x :: xs_ -> reverseListNaive xs_ ++ [x]
```

#### Approach B: Manual Tail-Recursive Accumulator (Fast, O(N) Time and Space)

By using a local helper function and prepending elements to an accumulator using the fast cons operator (::), we reverse the list in a single, tail-recursive pass.

```elm
reverseListTail : List String -> List String
reverseListTail xs =
    let
        reverseHelper acc remaining =
            case remaining of
                [] ->
                    acc
                head :: tail ->
                    reverseHelper (head :: acc) tail
    in
    reverseHelper [] xs
```

#### Approach C: Left Fold Library Shorthand (Most Idiomatic)

Since left-folding naturally traverses the list from left-to-right and prepends elements, we can pass the cons operator (::) directly to foldl.

```elm
reverseListFold : List String -> List String
reverseListFold xs =
    List.foldl (::) [] xs
```

### Problem 2: Guilt Calculator

**Goal:** Extract integer weights from a list of Clue records and calculate their sum.

#### Approach A: Map then Fold (Using Let-In)

We transform the list of records into a list of integers using the field accessor .weight as a mapping function, then fold over those integers.

```elm
type alias Clue = { description : String, weight : Int }

calculateGuiltMapFold : List Clue -> Int
calculateGuiltMapFold clues =
    let
        weights = List.map .weight clues
    in
    List.foldl (+) 0 weights
```

#### Approach B: The Pipelined Approach (Top-to-Bottom Flow)

An elegant refinement of Approach A that eliminates the need to declare a temporary local variable (weights) inside a let block.

```elm
calculateGuiltPipeline : List Clue -> Int
calculateGuiltPipeline clues =
    clues
        |> List.map .weight
        |> List.foldl (+) 0
```

#### Approach C: Single-Pass Fold (High Performance)

Instead of running two separate iteration passes (one mapping and one folding), we perform the record property lookup and addition concurrently inside a single pass.

```elm
calculateGuiltSinglePass : List Clue -> Int
calculateGuiltSinglePass clues =
    List.foldl (\clue acc -> clue.weight + acc) 0 clues
```

### Problem 3: Path/Breadcrumb Builder

**Goal:** Take a list of node strings (e.g., ["User", "Purchases", "Receipts"]) and construct a connected string separated by arrows ("User -> Purchases -> Receipts"), preventing empty elements and leading/trailing arrows.

#### Approach A: Right Fold with Conditional Validation

Using a right fold, we walk backward from right-to-left. If the accumulator is empty, we return the item; otherwise, we append the arrow separator. Writing the lambda inside a let block preserves clean formatting.

```elm
buildPathFoldr : List String -> String
buildPathFoldr nodes =
    let
        joinStep item acc =
            if acc == "" then
                item
            else
                item ++ " -> " ++ acc
    in
    List.foldr joinStep "" nodes
```

#### Approach B: Left Fold with Reversed Appending

We can achieve the same result using a left fold by swapping the concatenation order (appending the new item on the right of the accumulator), matching the left-to-right processing order.

```elm
buildPathFoldl : List String -> String
buildPathFoldl nodes =
    List.foldl (\item acc -> if acc == "" then item else acc ++ " -> " ++ item) "" nodes
```

#### Approach C: Structural Pattern Matching (Eliminating the If Guard)

The ultimate functional paradigm approach: instead of executing a conditional if check inside our loop at every step, we pattern-match the list structure first. We pull out the very first element as the base accumulator, then fold over the remaining list without any runtime branch logic.

```elm
buildPathPatternMatch : List String -> String
buildPathPatternMatch nodes =
    case nodes of
        [] ->
            ""
        first :: rest ->
            List.foldl (\item acc -> acc ++ " -> " ++ item) first rest
```

---

## Capture notes

- Source: hand-written study session (FP + Elm), captured 2026-07-01.
- Cleanups applied vs. the pasted original: removed terminal paste-escaping (`\=`, `\++`, `\>`, `\\`, `\_`, `\[\]`); fixed `:else` → `else` in Problem 3A; "Sorthand" → "Shorthand" (Problem 1C); "an conditional" → "a conditional" and "structural" → "Structural" (Problem 3C). Code logic is otherwise verbatim.
