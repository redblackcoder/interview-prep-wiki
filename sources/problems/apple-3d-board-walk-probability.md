# Apple Onsite — 3D Board Random-Walk Probability

**Company:** Apple (Compute Platform / control plane team). **Round:** coding, ~45 min, live editor. **Self-assessed:** algorithm + optimization correct; floating-point follow-up shaky.

## Problem as posed

- Board is a 3D cube, **8×8×8** (the 3D analog of an 8×8 chessboard).
- A single piece moves **diagonally**: from `(x,y,z)` to `(x±1, y±1, z±1)` → **8 moves**.
- Make **N random moves**, each uniform over the 8 directions (off-board picks allowed).
- **Compute the probability of NOT stepping off the board** during all N moves. (P(step out) = 1 − answer; interviewer wanted P(stay).)

Clarifications that were established live: any random move (not a fixed sequence); one step per move (±1 only); inputs are start `(x,y,z)` and move count `N`; board size is fixed.

## Solution path taken

1. Reduced to 2D first to reason, then generalized to 3D (4 diagonals → 8).
2. Framed the recurrence: from a cell, each of 8 directions has prob 1/8; valid ones carry probability forward, off-board ones contribute 0.
   - `f(cell, 1) = validMoves(cell)/8`
   - `f(cell, k) = Σ over valid neighbors m of (1/8)·f(m, k-1)`
3. First wrote the **exponential recursion** (`O(8^N)`).
4. When asked to improve, recognized repeated `(cell, k)` sub-states → **bottom-up DP** over a `[8][8][8][N]` table. **O(N·8⁴) = O(N)** since the board is fixed.

## What went well

- Clean 2D→3D reduction and honest think-aloud deriving the recurrence.
- Self-proposed the memoization when prompted to optimize.
- Correct complexity: exponential → linear in N; correctly argued the `8⁴` factor is constant.

## What to fix

- **A real bug in the DP return:** returned `subsol[...][n]` where the last dimension has size `n` (indices `0..n-1`) → `ArrayIndexOutOfBoundsException`. Should be `[n-1]`. (Not caught live; `main` was empty so nothing ran.)
- **The `double`-precision follow-up was answered wrong.** Interviewer asked at what N to worry about `double` precision as the probability shrinks. The answer given — **N ≈ 52/3 ≈ 18** — came from treating "divide by 8" as *consuming mantissa bits*. It does not: dividing by a power of two is an **exact exponent adjustment**, no mantissa loss. Interviewer hinted exactly this ("we subtract 3 from the exponent, we're not shifting the mantissa").

## The correct floating-point answer (the learning)

IEEE 754 `double` = 1 sign + 11 exponent + 52 stored mantissa bits (53 effective).

- **Relative precision (~2⁻⁵³, ~15–16 decimal digits) is preserved at any magnitude** — a small probability like 1e-50 is stored just as accurately as 0.5. "Value gets small" is *not itself* a precision problem; that's the point of a floating exponent.
- The real limits: **underflow to 0** near ~10⁻³⁰⁸ (normalized) / ~5e-324 (subnormal) → an N in the **hundreds–thousands**, not 18; and **summation rounding** ~N·2⁻⁵² relative (~1e-14 at N=18, negligible).
- **Exact fixes:** track the **integer count of surviving paths** (probability = count / 8ᴺ). `long` overflows ~N=21 (8²¹ > Long.MAX), so use **`BigInteger`** for exactness at any N; `BigDecimal` if you want the decimal directly.

## Verdict on the round

The core signal (model → correct recurrence → self-directed DP → correct complexity) is the strong, primary axis and was solid. The float fumble is a depth ding on a bonus probe, not a coding failure — likely lowers a strong-hire toward hire rather than causing a reject, though at a tight staff bar IEEE-754 fundamentals are exactly the kind of thing that tips a close call.
