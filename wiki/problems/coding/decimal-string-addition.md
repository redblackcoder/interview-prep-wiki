# Decimal string addition

## Problem

Add two nonnegative decimals represented as strings, with potentially different numbers of digits on either side of the point. Return their exact decimal sum without floating-point rounding.

## Example

For 9.95 + 0.1, align the fractional parts as 95 and 10 hundredths. Their sum carries a unit into the integer part, giving 10.05. Carry can cross both the point and the original leading digit.

## Recorded approach

Align the fractional digits and propagate the carry from right to left.

[Source material](../../../sources/code/java-practice/practice/prep/problems/AddDecimals.java)
