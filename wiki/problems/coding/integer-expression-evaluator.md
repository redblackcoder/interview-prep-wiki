# Integer expression evaluator

## Problem

Evaluate integer expressions with parentheses, unary minus and the four arithmetic operators. Resolve precedence before evaluating and distinguish unary minus from subtraction.

## Example

In −(1+4)×2, the parenthesized sum is 5; negation and multiplication give −10. In 8−3−2 the subtractions associate to the left, giving 3.

## Recorded approach

Tokenize operators in context, convert to postfix using precedence, then evaluate on an operand stack.

[Source material](../../../sources/code/java-practice/practice/prep/problems/Calculator.java)
