# Bounded diagonal walk

## Problem

A piece on an 8×8×8 board independently chooses +1 or −1 on each axis at every move. All eight directions are equally likely. Return the probability that the piece stays on the board for all N moves.

## Example

At a corner, only one of eight moves stays on the board, so one-step survival is 1/8. Zero moves from any valid starting cell survive with probability 1. Renormalizing over valid neighbors would incorrectly give the corner probability as 1.

## Recorded approach

Propagate surviving probability through valid neighbors at each step, keeping the probability of all possible moves in the denominator.

[Source material](../../../sources/code/bounded-walk-probability/Solution.java)

## Retrospective study notes

**Round:** coding, ~45 min, live editor.

### Problem as posed

- Board is a 3D cube, **8×8×8** (the 3D analog of an 8×8 chessboard).
- A single piece moves **diagonally**: from `(x,y,z)` to `(x±1, y±1, z±1)` → **8 moves**.
- Make **N random moves**, each uniform over the 8 directions (off-board picks allowed).
- **Compute the probability of NOT stepping off the board** during all N moves. (P(step out) = 1 − answer; the ask was P(stay).)

Clarifications established: any random move (not a fixed sequence); one step per move (±1 only); inputs are start `(x,y,z)` and move count `N`; board size is fixed.

### Solution path

1. Reduce to 2D first to reason, then generalize to 3D (4 diagonals → 8).
2. Frame the recurrence: from a cell, each of 8 directions has prob 1/8; valid ones carry probability forward, off-board ones contribute 0.
   - `f(cell, 1) = validMoves(cell)/8`
   - `f(cell, k) = Σ over valid neighbors m of (1/8)·f(m, k-1)`
3. The direct **exponential recursion** is `O(8^N)`.
4. To improve: repeated `(cell, k)` sub-states → **bottom-up DP** over a `[8][8][8][N]` table. **O(N·8⁴) = O(N)** since the board is fixed.

### Pitfalls

- **Off-by-one in the DP return:** with the last dimension of size `n` (indices `0..n-1`), the answer is `subsol[...][n-1]`; reading `subsol[...][n]` throws `ArrayIndexOutOfBoundsException`.
- **The `double`-precision follow-up:** at what N should you worry about `double` precision as the probability shrinks? Treating "divide by 8" as *consuming mantissa bits* gives **N ≈ 52/3 ≈ 18** — and is wrong. Dividing by a power of two is an **exact exponent adjustment**: subtract 3 from the exponent, no mantissa shift, no mantissa loss.

### The correct floating-point answer

IEEE 754 `double` = 1 sign + 11 exponent + 52 stored mantissa bits (53 effective).

- **Relative precision (~2⁻⁵³, ~15–16 decimal digits) is preserved at any magnitude** — a small probability like 1e-50 is stored just as accurately as 0.5. "Value gets small" is *not itself* a precision problem; that's the point of a floating exponent.
- The real limits: **underflow to 0** near ~10⁻³⁰⁸ (normalized) / ~5e-324 (subnormal) → an N in the **hundreds–thousands**, not 18; and **summation rounding** ~N·2⁻⁵² relative (~1e-14 at N=18, negligible).
- **Exact fixes:** track the **integer count of surviving paths** (probability = count / 8ᴺ). `long` overflows ~N=21 (8²¹ > Long.MAX), so use **`BigInteger`** for exactness at any N; `BigDecimal` if you want the decimal directly.
