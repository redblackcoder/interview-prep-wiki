# Bounded Random-Walk Probability (State-Space DP)

A whole family of interview problems reduces to one move: *"a piece takes N random steps on a bounded grid — what's the probability it survives / lands somewhere / how many paths?"* The naive answer explores the **O(branching^N)** move tree. The pattern is to see that the tree revisits the same `(position, steps-remaining)` states over and over, and **memoize on that bounded state** — collapsing the exponential tree to **O(N · cells · branching)**, i.e. linear in N when the board is fixed. The 2D version is the classic *Knight Probability in a Chessboard* (LeetCode 688); the Apple onsite here is the 3D diagonal cube.

## The problem it solves

Given a start cell on a bounded board, a fixed move-set (branching factor `B`), and `N` random moves each chosen **uniformly over all B directions** (including ones that leave the board), compute the probability that **all N moves stay on the board**.

Concrete instance (Apple, 8×8×8 cube, diagonal moves `(x±1, y±1, z±1)`, `B=8`): survive = never step off in N moves. Off-board picks are still *chosen* with prob 1/8 — they just kill that path.

## The recurrence (the actual insight)

Define `f(cell, k)` = probability of surviving `k` moves starting at `cell`.

```
f(cell, 1) = validMoves(cell) / B            # last move: fraction of dirs that stay on
f(cell, k) = Σ over valid neighbors m of  (1/B) · f(m, k-1)
```

Two things make it click:

- **Each direction is chosen with prob 1/B regardless of validity.** A move off the board contributes **0** (that path is dead), so only valid neighbors carry probability forward. You do *not* renormalize to "1/validCount" — that would be a different problem (choose only among legal moves).
- **The state is `(cell, k)`, and it's bounded.** `cell` ranges over a fixed number of board cells; `k` over `1..N`. The move tree has up to `B^N` leaves but only `cells · N` distinct states — that gap is the entire optimization.

Equivalent framing (often cleaner to reason about, and exact): **count surviving paths.** `paths(cell, k)` is an integer with the same recurrence minus the `1/B`; the probability is `paths / B^N`. Same DP, no floating point — see [[theory/floating-point-representation]].

## Two implementations

**Bottom-up DP** — fill `k = 1, 2, …, N`; layer `k` reads only layer `k-1`:

```java
static double surviveProb(int sx, int sy, int sz, int n) {
    double[][][][] dp = new double[8][8][8][n];      // dp[x][y][z][k-1] = f(cell, k)
    for (int k = 1; k <= n; k++)
        for (int x = 1; x <= 8; x++)
            for (int y = 1; y <= 8; y++)
                for (int z = 1; z <= 8; z++) {
                    if (k == 1) {
                        dp[x-1][y-1][z-1][0] = validMoves(x, y, z).size() / 8.0;
                    } else {
                        double p = 0.0;
                        for (int[] m : validMoves(x, y, z))
                            p += dp[m[0]-1][m[1]-1][m[2]-1][k-2] / 8.0;
                        dp[x-1][y-1][z-1][k-1] = p;
                    }
                }
    return dp[sx-1][sy-1][sz-1][n-1];                // n moves -> index n-1  (NOT [n])
}
```

**Top-down memoized** — the same states, filled lazily; usually less error-prone to *write* because you don't hand-manage the layer ordering or the base-index arithmetic:

```java
Double[][][][] memo;                                 // null = uncomputed
double f(int x, int y, int z, int k) {
    if (k == 0) return 1.0;                           // 0 moves left = already survived
    if (memo[x][y][z][k] != null) return memo[x][y][z][k];
    double p = 0.0;
    for (int[] m : validMoves(x, y, z)) p += f(m[0], m[1], m[2], k-1) / 8.0;
    return memo[x][y][z][k] = p;
}
```

Only ~`cells·N` of the `B^N` calls do real work; the rest are memo hits.

## Complexity

- **Naive recursion / full move tree:** `O(B^N)`. Boundary pruning shrinks it (dead branches stop) but it stays exponential.
- **DP / memoized:** `states × work-per-state = (cells · N) × B`. For the 8×8×8 cube: `512 · N · 8 = N · 8⁴`. The board is fixed, so **8⁴ is a constant ⇒ O(N), linear**. This "8⁴ is a constant, so it's linear in N" is the exact answer the Apple interviewer was fishing for.
- **Space:** `O(cells · N)` naive; **`O(cells)`** if you keep only the previous `k` layer (the recurrence reaches back just one step).

## Traps to avoid

- **Off-by-one on the answer index.** With `dp[...][k-1]` holding "k moves," the N-move answer is at index `n-1`, not `n`. Writing `dp[...][n]` on an array sized `n` throws `ArrayIndexOutOfBounds` — the exact bug in the live Apple attempt. The top-down form sidesteps this.
- **Renormalizing to valid moves only.** `Σ (1/validCount)` answers "the piece only ever picks legal moves" — a *different* question. Survival uses `1/B` and lets off-board paths die.
- **Wrong base case.** `f(cell, 1) = validCount/B` (top-down: `f(·,0)=1`). Getting the base one step off silently shifts every probability.
- **Floating-point at large N.** The product/sum of many `<1` factors shrinks; know *why* `double` is fine well into the hundreds of moves (relative precision is magnitude-independent) and when to switch to exact `BigInteger` path-counts. See [[theory/floating-point-representation]].
- **Forgetting the layer dependency.** Bottom-up must fill `k-1` fully before `k`. Get the loop nesting wrong (cells inside k, not k inside cells) or it reads uncomputed zeros.

## Recognizing the pattern

Reach for it whenever a problem is *"N random/possible steps on a bounded structure, want a probability or a count":*

- **Knight Probability in Chessboard** (LC 688) — the 2D twin of this exact problem.
- **Out-of-Boundary Paths** (LC 576) — count paths that *leave*; complementary counting on the same DP.
- **Dice-roll / coin-path counting**, **Knight Dialer** (LC 935), random-walk return/absorption probabilities.
- Any "probability after N steps" — it's a length-N power of a transition, and DP over `(state, step)` *is* iterating that transition. See [[theory/state-machine-replication]] for the transition-matrix view; the matrix-power form gives `O(cells³ · log N)` when N ≫ cells.

## Interview angle

> "I model it as `f(cell, k)` = probability of surviving k moves, with `f(cell, k) = Σ over on-board neighbors of (1/8)·f(neighbor, k-1)` and base `f(cell,1) = validMoves/8`. Key point: every direction is picked with prob 1/8 whether or not it's legal, so off-board picks just contribute zero — I don't renormalize. The move tree is O(8^N), but the state `(cell, k)` is bounded — 512 cells times N — so I memoize and it's O(N·8⁴), linear in N since the board is fixed. If N is large I'd switch from `double` probabilities to an exact `BigInteger` count of surviving paths over 8^N, which also sidesteps floating-point underflow."

## Connections
- [[theory/floating-point-representation]] — the `double`-precision follow-up this problem triggers: why relative precision is magnitude-independent, when underflow bites, and the exact `BigInteger` path-count alternative
- [[algorithms/knapsack-variants]] — sibling "DP over a bounded state space (item/capacity vs cell/steps), each state O(choices) work" family; both collapse an exponential choice tree by memoizing bounded state
- [[theory/state-machine-replication]] — "probability after N steps" is applying a transition N times; the matrix-power view gives an O(cells³·log N) alternative when N dominates
- [[coding-patterns/fold-accumulator]] — bottom-up DP is a fold over the step index, threading the previous layer as the accumulator

## Sources
- [[sources/problems/apple-3d-board-walk-probability]] — the Apple onsite: problem, live solution path, the `[n]` vs `[n-1]` bug, and the floating-point follow-up
- Runnable code in raw (DP + recursion + exact BigInteger cross-check): [bounded-walk-probability/](https://github.com/redblackcoder/interview-prep-raw/blob/main/code/bounded-walk-probability/)
