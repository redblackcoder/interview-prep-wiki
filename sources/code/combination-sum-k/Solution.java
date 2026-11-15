import java.util.*;

/**
 * Combinations of numbers from the fixed set {1,2,...,9} that sum to a target.
 *
 * Two variants:
 *   1. findCombinations(n)      -- any number of distinct integers summing to n
 *   2. findCombinations(n, k)   -- exactly k distinct integers summing to n
 *
 * Rules: within one combination a number is not repeated; the same number may
 * appear across different combinations. The set is fixed at 1..9.
 *
 * Approach: backtracking over the include/exclude decision tree. At each step we
 * either take the "next" candidate (1..9) or skip it, carrying a running sum so
 * we never re-sum the partial combination.
 */
public class Solution {

    // ---- Variant 2: exactly k integers summing to n ----
    public static List<List<Integer>> findCombinations(int n, int k) {
        List<List<Integer>> sol = new ArrayList<>();
        buildCombination(1, new ArrayList<>(), 0, n, k, sol);
        return sol;
    }

    private static void buildCombination(int next, List<Integer> curr, int currSum,
                                         int n, int k, List<List<Integer>> sol) {
        // Found: right size AND right sum. Stop -- nothing more can be added.
        if (curr.size() == k && currSum == n) {
            sol.add(new ArrayList<>(curr));
            return;
        }
        // Prune: ran out of candidates, or already have k elements with the wrong sum.
        if (next == 10 || curr.size() == k) {
            return;
        }
        // Take `next`.
        curr.add(next);
        buildCombination(next + 1, curr, currSum + next, n, k, sol);
        curr.remove(curr.size() - 1);
        // Skip `next`.
        buildCombination(next + 1, curr, currSum, n, k, sol);
    }

    // ---- Variant 1: any count of integers summing to n (no k) ----
    public static List<List<Integer>> findCombinations(int n) {
        List<List<Integer>> sol = new ArrayList<>();
        buildAny(1, new ArrayList<>(), 0, n, sol);
        return sol;
    }

    private static void buildAny(int next, List<Integer> curr, int currSum,
                                 int n, List<List<Integer>> sol) {
        if (currSum == n) {
            sol.add(new ArrayList<>(curr));
            return;
        }
        // Prune: overshot, or out of candidates.
        if (currSum > n || next == 10) {
            return;
        }
        curr.add(next);
        buildAny(next + 1, curr, currSum + next, n, sol);
        curr.remove(curr.size() - 1);
        buildAny(next + 1, curr, currSum, n, sol);
    }

    public static void main(String[] args) {
        // n = 9, k = 3  ->  [1,2,6] [1,3,5] [2,3,4]
        for (List<Integer> combo : findCombinations(9, 3)) {
            System.out.println(combo);
        }
    }
}
