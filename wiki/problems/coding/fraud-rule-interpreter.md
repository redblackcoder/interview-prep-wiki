# Fraud rule interpreter

## Problem

Read ordered accept/block rules containing case-sensitive string comparisons, Boolean fields and nested AND/OR groups. Return the action of the first rule whose condition matches.

## Example

In a comparison against the quoted value “A AND B”, AND belongs to the string. An AND after the closing quote combines predicates. A parser that splits the entire line on spaces cannot distinguish those cases.

## Recorded approach

Evaluate rules in order, parse quoted strings without splitting on embedded operators, and compose equality and Boolean clauses into a nested predicate tree.

### Part 1: String Equality Rules

- Built simple Java program: main runner with in-memory test cases, FraudDetector class taking rules at construction
- Parser splits on space after accept/block, uses regex for variable, string pattern for value; ignores 'if' keyword
- Confirmed first-applicable-rule short-circuits, and matching is case sensitive (assumed, not specified)

### Part 2: Boolean Clauses & Compound Rules

- Introduced Clause interface with equality and Boolean implementations; Rule combines up to two clauses with and/or
- Parser splits on top-level operator while ignoring operators inside quoted strings

### Part 3: Arbitrary Nesting

- Refactored to tree-structured composite clauses; parser uses backtracking to handle nested parentheses
- Quoted-string handling from Part 2 keeps solution robust against operator substrings like 'AND' inside values

[Anonymized session notes](../../../sources/notes/coding/fraud-rule-interpreter.md)
