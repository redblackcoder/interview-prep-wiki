# Fraud rule interpreter

Anonymized session notes.

## Part 1: String Equality Rules

- Built simple Java program: main runner with in-memory test cases, FraudDetector class taking rules at construction
- Parser splits on space after accept/block, uses regex for variable, string pattern for value; ignores 'if' keyword
- Confirmed first-applicable-rule short-circuits, and matching is case sensitive (assumed, not specified)

## Part 2: Boolean Clauses & Compound Rules

- Introduced Clause interface with equality and Boolean implementations; Rule combines up to two clauses with and/or
- Parser splits on top-level operator while ignoring operators inside quoted strings

## Part 3: Arbitrary Nesting

- Refactored to tree-structured composite clauses; parser uses backtracking to handle nested parentheses
- Quoted-string handling from Part 2 keeps solution robust against operator substrings like 'AND' inside values
