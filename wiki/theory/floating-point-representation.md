# Floating-Point Numbers: IEEE 754, Precision, and Underflow

The question that exposes whether you actually understand floating point: *"this probability shrinks as N grows — at what N does `double` precision break down?"* The tempting wrong answer treats "divide by 8" as eating mantissa bits and lands on N≈18. The right answer requires knowing that **a `double` carries a fixed number of *significant* digits at *any* magnitude**, because the exponent floats — so smallness alone costs nothing, and the real failure modes are **underflow, rounding on inexact results, and cancellation**, none of which bite at N=18. This page builds the model from the math up to that conclusion.

## The core problem floating point solves

The reals are uncountable and unbounded; a machine word has 64 bits, so it can name only 2⁶⁴ distinct values. Any finite scheme must choose **which** reals to represent and accept that everything else rounds to the nearest chosen value. Two obvious schemes and why they lose:

- **Fixed-point** (integer with an implied binary point at a fixed position) gives *uniform absolute* spacing. To represent both a galaxy's mass and an electron's, you'd need hundreds of bits — most wasted. Fine for money (bounded range, exact cents); useless for scientific range.
- **Floating point** gives *uniform relative* spacing: it stores a few significant digits plus a scale, exactly like scientific notation. It trades a constant *absolute* precision for a constant *relative* precision — and relative is what almost all computation actually wants.

> **The one idea:** floating point keeps ~16 significant *decimal* digits and a separate exponent for scale. So `1.234e-300` is stored just as precisely (in relative terms) as `1.234e+300`. "The number is small" is **not** a precision problem — that's the entire reason the exponent is separate.

## Scientific notation → IEEE 754 binary64

Decimal scientific notation writes `x = ±d.dddd × 10^e` with one nonzero digit before the point (*normalized*). Binary floating point is identical in base 2:

```
x = (-1)^sign × 1.fffff…f (base 2) × 2^exponent
                └── 52 fraction bits ──┘
```

Because a normalized binary significand *always* starts with `1.`, that leading 1 is **not stored** — it's implicit. You get 53 bits of significand for the price of 52. This is the "hidden bit" trick and it's why doubles have 53 bits of precision from a 52-bit field.

A **binary64 `double`** lays out its 64 bits as:

```
 ┌─┬───────────┬────────────────────────────────────────────────────┐
 │S│  exponent │                    fraction (mantissa)              │
 │1│   11 bits │                       52 bits                       │
 └─┴───────────┴────────────────────────────────────────────────────┘
  63 62      52 51                                                   0
```

- **S (1 bit)** — sign. 0 = positive. (Sign is separate, so there are +0 and −0.)
- **exponent (11 bits)** — stored as a **biased** unsigned value E ∈ [0, 2047]; the true exponent is `e = E − 1023`. Bias avoids a separate sign bit for the exponent and makes bit-pattern order match numeric order. Normal numbers use E ∈ [1, 2046] ⇒ e ∈ [−1022, +1023]. E=0 and E=2047 are reserved (below).
- **fraction (52 bits)** — the bits after the implicit `1.`, i.e. the significand's precision.

**Value of a normal double:** `(-1)^S × (1 + fraction/2^52) × 2^(E-1023)`.

### Worked encoding — the number 0.125

`0.125 = 1.0 × 2^-3`. So significand = `1.0` (fraction bits all 0), true exponent e = −3, stored E = −3 + 1023 = 1020 = `01111111100`.

```
S=0  E=01111111100 (1020)  fraction=0000…0
= +1.0 × 2^-3 = 0.125     ← exact, because 0.125 is a power of two
```

Contrast `0.1`: it's `0.0001100110011…` repeating in binary — **not** representable exactly in any finite binary significand, so it rounds to the nearest double (`0.1000000000000000055…`). This is why `0.1 + 0.2 != 0.3` in every IEEE-754 language. Powers of two and their finite sums are exact; most decimals are not.

## The three quantities you must be able to quote

| Quantity | Value | Meaning |
|---|---|---|
| **Machine epsilon** `ε = 2⁻⁵²` | ≈ 2.22×10⁻¹⁶ | gap between 1.0 and the next double; ~**15–16 significant decimal digits** |
| **Largest finite** | ≈ 1.80×10³⁰⁸ | `(2−2⁻⁵²)×2¹⁰²³`; beyond it → `+Inf` (**overflow**) |
| **Smallest normal** | ≈ 2.23×10⁻³⁰⁸ | `1.0×2⁻¹⁰²²`; below it you enter subnormals |
| **Smallest subnormal** | ≈ 4.94×10⁻³²⁴ | `2⁻¹⁰⁷⁴`; below it → `0` (**underflow**) |

**Reserved exponent codes:** E=2047 encodes `±Inf` (fraction 0) and `NaN` (fraction ≠ 0); E=0 encodes `±0` (fraction 0) and **subnormals** (fraction ≠ 0). Subnormals drop the implicit leading 1 to represent values below the smallest normal, trading precision for a graceful slide to zero ("gradual underflow") instead of a cliff.

## The load-bearing concept: precision is RELATIVE (ULP)

For a number whose exponent is `e`, consecutive representable values are `2^(e−52)` apart. This gap is one **ULP** (unit in the last place). The crucial consequence:

```
around 1.0    (e=0)    ULP = 2^-52  ≈ 2.2e-16   ← ~16 digits of precision
around 10^6   (e≈20)   ULP ≈ 2.4e-10            ← still ~16 sig digits
around 10^-300 (e≈-997) ULP ≈ 2^-1049 ≈ 2e-316  ← STILL ~16 sig digits
```

**The gaps scale with the magnitude, so the *relative* gap `ULP/x ≈ 2⁻⁵²` is (almost) constant everywhere.** A double always pins down its value to ~1 part in 2⁵³ — whether the value is astronomical or minuscule. Round-to-nearest guarantees any representable-range real is stored with **relative error ≤ ½ ULP ≈ 2⁻⁵³**.

This is the fact the interview hinged on: making a probability *smaller* moves it to a region with *smaller absolute* ULP but the *same relative* precision. You keep your ~16 significant digits the whole way down — until you hit the exponent floor and underflow.

## Why dividing by a power of two is EXACT

Take `x = significand × 2^e`. Then `x / 8 = significand × 2^(e−3)`. Multiplying by `2⁻³` is a pure **exponent decrement of 3** — the 52 significand bits are copied unchanged. No bits are lost, no rounding occurs.

```
x    = 1.011010…011 × 2^e        (some significand)
x/8  = 1.011010…011 × 2^(e-3)    (SAME significand, exponent -3)  ← exact
```

So the interview intuition "÷8 shifts the mantissa by 3 bits and after 52/3≈18 steps the mantissa is exhausted" is **wrong twice over**: (1) ÷8 adjusts the *exponent*, not the stored mantissa; (2) nothing accumulates in the mantissa to "run out." The only limit from repeated ÷8 is the exponent hitting −1022 (then subnormal, then 0) — that's **~340 divisions** (1022/3) to reach the smallest normal, ~358 to flush to zero. Not 18.

## Where floating-point error ACTUALLY comes from

Since scaling and exact-representable operands are error-free, real error enters through:

1. **Rounding of an inexact result.** `a ⊕ b` computes the true `a+b` then rounds to the nearest double — ≤ ½ ULP each op. One operation is ~2⁻⁵³ relative; harmless in isolation.
2. **Accumulation over many ops.** Summing `n` values accumulates rounding: worst case ~`n·ε` relative, typically ~`√n·ε` (errors partially cancel). For `n=1000`: ~`1000·2⁻⁵² ≈ 2×10⁻¹³` — still 12–13 good digits. Compensated summation (Kahan) knocks this back toward ε if you need it.
3. **Catastrophic cancellation** — the real killer. Subtracting two nearly-equal numbers annihilates the leading (correct) digits and promotes rounding noise into significance: `1.2345678 − 1.2345670` keeps ~1 good digit out of 8. **Only subtraction of close values does this**; summing positives never cancels.
4. **Non-associativity.** `(a+b)+c ≠ a+(b+c)` in general, so sum order changes the result. Sum smallest-to-largest to minimize error.
5. **Overflow / underflow** — leaving the exponent range: `→ ±Inf` above ~10³⁰⁸, `→ 0` below ~10⁻³²⁴ (losing precision gradually through subnormals first).

## Applying all of it to the interview problem

The DP computes `f(cell, k) = Σ over ≤8 neighbors of f(neighbor, k−1)/8` — a probability that shrinks as `k` grows. "When does `double` break?"

- **Smallness is free.** The value drifts toward 0 but keeps ~16 significant digits the whole way, because relative precision is magnitude-independent. There is **no special event at N=18**.
- **The ÷8 is exact** (power of two) — contributes zero error.
- **The Σ is over positive, same-order terms** — no cancellation (trap #3 absent), and same-magnitude addends minimize rounding. Accumulated error after N steps is ~`N·2⁻⁵²`: **~2×10⁻¹⁵ at N=18, ~1×10⁻¹³ at N=1000.** Negligible either way.
- **The only real limit is underflow to 0.** The survival probability decays roughly geometrically, `~r^N` for some per-step factor `r<1`. It underflows near `10⁻³⁰⁸`, i.e. when `N·log₁₀(1/r) ≳ 308`. Even a fast decay of `r≈0.5` gives `N ≈ 308/0.30 ≈ 1000`; realistic interior walks decay slower, pushing it higher. **So `double` is safe into the hundreds–low-thousands of moves**, not 18. Before hard underflow, subnormals cost precision gradually starting ~10⁻³⁰⁸.

**Correct answer to give:** *"Smallness doesn't hurt a `double` — relative precision (~16 digits) is preserved at any magnitude because the exponent floats, and ÷8 is an exact exponent shift. The summation is positive-only so there's no cancellation; accumulated rounding is ~N·2⁻⁵², still ~13 digits at N=1000. The real limit is underflow to zero near 10⁻³⁰⁸, which for a geometrically-decaying survival probability is an N in the hundreds to thousands. If I needed exactness or larger N, I'd count surviving paths as integers and divide by 8ᴺ, or work in log-space."*

## Escaping the limits when you truly need to

- **Exact integer counting.** Track the **count of surviving paths** (probability = count / 8ᴺ). No floating point, no underflow. But `long` overflows at **N≈21** — `8²¹ = 2⁶³`, exactly one past `Long.MAX = 2⁶³−1` — so use **`BigInteger`** for exactness at any N. (This was the "return the count instead of a probability" idea from the interview; note it needs BigInteger, since `long` is *worse* than `double` for large N.)
- **`BigDecimal`** — arbitrary-precision decimal; exact scaling and configurable rounding, at a large speed/memory cost.
- **Log-space.** Store `log p` and replace multiply with add (`log(ab)=log a+log b`); combine sums via **log-sum-exp**. Range becomes ~10^(±10⁸) — underflow effectively gone. Standard in HMMs/Bayesian inference where products of many small probabilities are the norm.

## Key points
- `double` = 1 sign + 11 biased-exponent + 52 fraction bits; implicit leading 1 ⇒ **53 significant bits ≈ 15–16 decimal digits**.
- Precision is **relative**: `ULP/x ≈ 2⁻⁵²` at every magnitude; gaps scale with the number, so a small value is stored as precisely (relatively) as a large one.
- **Multiplying/dividing by a power of two is exact** — it only shifts the exponent; the mantissa is untouched. "÷8 exhausts the mantissa at N≈18" is a misconception.
- Error comes from **rounding inexact results, accumulation, and — above all — cancellation** (subtracting near-equal values). Summing positives never cancels.
- The range limits are **overflow (~10³⁰⁸ → Inf)** and **underflow (~10⁻³²⁴ → 0, gradual via subnormals from ~10⁻³⁰⁸)**.
- For the walk problem: `double` is fine into the hundreds–thousands of moves; the ceiling is underflow, not mantissa exhaustion. Exact alternatives: **`BigInteger` path counts** (÷8ᴺ), `BigDecimal`, or **log-space**.

## Interview angle

> "A `double` is sign + 11-bit biased exponent + 52-bit fraction with an implicit leading 1, so 53 significant bits — about 16 decimal digits. The precision is *relative*: the gap between representable values scales with the magnitude, so a probability near 1e-300 is stored just as precisely, relatively, as one near 1. So the number getting small doesn't cost precision — that's the whole point of a floating exponent. Dividing by 8 is exact because it just decrements the exponent by 3; the mantissa is untouched, so there's no 'running out of mantissa' at N≈18. Real error would come from cancellation, but my sum is all positive terms so nothing cancels, and accumulated rounding is about N times 2⁻⁵² — still 13-plus good digits at N=1000. The actual wall is underflow to zero near 1e-308, which for a geometrically decaying probability is an N in the hundreds to thousands. If I needed exactness or a larger N, I'd count surviving paths as BigIntegers and divide by 8-to-the-N, or move to log-space."

## Connections
- [[coding-patterns/bounded-walk-probability-dp]] — the Apple problem that raised this; the shrinking survival probability is what the precision question is about
- [[theory/durability-math]] — also multiplies many small probabilities (AFR³·(MTTR/yr)²); same "tiny numbers, order-of-magnitude reasoning" mindset, where log/relative thinking keeps you honest
- [[theory/latency-numbers]] — the other "know the constants cold" fundamentals table; ULP/epsilon/exponent-range are the floating-point counterparts

## Sources
- [[sources/problems/apple-3d-board-walk-probability]] — the onsite where the `double`-precision follow-up was asked (and the N≈18 misconception surfaced), with the corrected reasoning
