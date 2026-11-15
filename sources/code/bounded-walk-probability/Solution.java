import java.math.BigInteger;
import java.util.*;

/*
 * Coding interview problem (3D board + probability).
 *
 * An 8x8x8 board holds a single piece. From (x,y,z) the piece moves diagonally
 * to (x±1, y±1, z±1) -> 8 possible moves. Starting from a given cell we make N
 * random moves, each chosen uniformly from the 8 directions (including ones that
 * fall off the board). Compute the probability that ALL N moves stay on the board.
 *
 * Key recurrence (survive = never leave):
 *   f(cell, k) = probability of surviving k moves starting at cell
 *   f(cell, 1) = validMoves(cell) / 8
 *   f(cell, k) = sum over valid neighbors m of  (1/8) * f(m, k-1)
 *
 * The move-tree is O(8^N). Memoizing on the bounded state (cell, k) collapses it
 * to O(N * cells * 8) = O(N * 8^4) = O(N) since the board is a fixed 8x8x8.
 */
class Solution {

  private static final int SIZE = 8;
  private static final int[][] MOVES = {
    {-1, -1, -1}, {-1, -1, 1}, {-1, 1, -1}, {-1, 1, 1},
    { 1, -1, -1}, { 1, -1, 1}, { 1, 1, -1}, { 1, 1, 1}
  };

  // ---- Bottom-up DP (double). This is the intended answer. ----
  // subsol[x][y][z][k-1] = probability of surviving k moves from (x+1,y+1,z+1).
  static double surviveProbDP(int xx, int yy, int zz, int n) {
    double[][][][] subsol = new double[SIZE][SIZE][SIZE][n];
    for (int k = 1; k <= n; k++) {
      for (int x = 1; x <= SIZE; x++) {
        for (int y = 1; y <= SIZE; y++) {
          for (int z = 1; z <= SIZE; z++) {
            if (k == 1) {
              subsol[x - 1][y - 1][z - 1][0] = validMoves(x, y, z).size() / 8.0;
            } else {
              double p = 0.0;
              for (int[] m : validMoves(x, y, z)) {
                p += subsol[m[0] - 1][m[1] - 1][m[2] - 1][k - 2] / 8.0;
              }
              subsol[x - 1][y - 1][z - 1][k - 1] = p;
            }
          }
        }
      }
    }
    // NOTE: the last dimension has size n, so [...][n] would be ArrayIndexOutOfBounds.
    // The value for n moves lives at index n-1.
    return subsol[xx - 1][yy - 1][zz - 1][n - 1];
  }

  // ---- Naive recursion (exponential). Kept to show the starting point / cross-check. ----
  static double surviveProbRec(int x, int y, int z, int movesMade, int n) {
    if (movesMade == n) {
      return validMoves(x, y, z).size() / 8.0;
    }
    double p = 0.0;
    for (int[] m : validMoves(x, y, z)) {
      p += surviveProbRec(m[0], m[1], m[2], movesMade + 1, n) / 8.0;
    }
    return p;
  }

  // ---- Exact variant: count surviving paths with BigInteger, probability = paths / 8^n. ----
  // Avoids ALL floating-point error and never underflows. `long` would overflow ~n=21
  // (8^21 > Long.MAX_VALUE), so use BigInteger for an exact answer at any n.
  static BigInteger survivePaths(int xx, int yy, int zz, int n) {
    BigInteger[][][] cur = new BigInteger[SIZE][SIZE][SIZE];
    for (int x = 1; x <= SIZE; x++)
      for (int y = 1; y <= SIZE; y++)
        for (int z = 1; z <= SIZE; z++)
          cur[x - 1][y - 1][z - 1] = BigInteger.valueOf(validMoves(x, y, z).size()); // k = 1

    for (int k = 2; k <= n; k++) {
      BigInteger[][][] next = new BigInteger[SIZE][SIZE][SIZE];
      for (int x = 1; x <= SIZE; x++)
        for (int y = 1; y <= SIZE; y++)
          for (int z = 1; z <= SIZE; z++) {
            BigInteger sum = BigInteger.ZERO;
            for (int[] m : validMoves(x, y, z))
              sum = sum.add(cur[m[0] - 1][m[1] - 1][m[2] - 1]);
            next[x - 1][y - 1][z - 1] = sum;
          }
      cur = next;
    }
    return cur[xx - 1][yy - 1][zz - 1]; // divide by 8^n for the probability
  }

  private static List<int[]> validMoves(int x, int y, int z) {
    List<int[]> moves = new ArrayList<>();
    for (int[] m : MOVES) {
      int nx = x + m[0], ny = y + m[1], nz = z + m[2];
      if (nx >= 1 && nx <= SIZE && ny >= 1 && ny <= SIZE && nz >= 1 && nz <= SIZE) {
        moves.add(new int[]{nx, ny, nz});
      }
    }
    return moves;
  }

  public static void main(String[] args) {
    int[][] tests = {{1, 1, 1, 1}, {1, 1, 1, 2}, {4, 4, 4, 3}, {1, 1, 1, 5}};
    for (int[] t : tests) {
      int x = t[0], y = t[1], z = t[2], n = t[3];
      double dp = surviveProbDP(x, y, z, n);
      double rec = surviveProbRec(x, y, z, 1, n);
      BigInteger paths = survivePaths(x, y, z, n);
      BigInteger total = BigInteger.valueOf(8).pow(n);
      System.out.printf("start(%d,%d,%d) n=%d  dp=%.10f  rec=%.10f  exact=%s/%s%n",
          x, y, z, n, dp, rec, paths, total);
    }
  }
}
