import java.util.*;

/*
 * Counting subarrays/substrings with EXACTLY k of something.
 *
 *   LC 992  Subarrays with K Different Integers
 *   LC 3306 Count of Substrings Containing Every Vowel and K Consonants II
 *
 * Two approaches, both O(n), both need a measure that only grows as the window grows:
 *   A. Single pass, range of valid starts: for each end r the valid starts form [start, hi];
 *      add hi - start + 1. hi depends on the measure (here: smallest last-seen index).
 *   B. atMost(k) - atMost(k - 1): at-most-k has valid starts [left, r], a plain sliding window.
 */
class Solution {

  // ---------- LC 992 ----------

  // A, as first written. Bug: after shrinking, the window is back to exactly k distinct, and
  // every start in [start, min(lsi)] is valid -- count++ assumes only one.
  // Fails on [1,2,1,1,3], k=2: returns 6, expected 7.
  static int distinctOriginal(int[] nums, int k) {
    Map<Integer, Integer> lsi = new HashMap<>();
    int n = nums.length, start = 0, end = 0, count = 0;
    while (end < n) {
      lsi.put(nums[end], end++);
      if (lsi.size() < k) {
      } else if (lsi.size() == k) {
        count += (min(lsi) - start + 1);
      } else {
        start = min(lsi);
        lsi.remove(nums[start++]);
        count++;                                  // BUG
      }
    }
    return count;
  }

  // A, fixed. The map can reach at most k + 1 (one new number per step), so one removal
  // restores exactly k. LinkedHashMap kept in last-seen order makes min(lsi) O(1).
  static int distinctRange(int[] nums, int k) {
    LinkedHashMap<Integer, Integer> lsi = new LinkedHashMap<>();
    int start = 0, count = 0;
    for (int end = 0; end < nums.length; end++) {
      lsi.remove(nums[end]);                      // re-insert to move to the back
      lsi.put(nums[end], end);
      if (lsi.size() > k) {
        var oldest = lsi.entrySet().iterator().next();
        start = oldest.getValue() + 1;
        lsi.remove(oldest.getKey());
      }
      if (lsi.size() == k) {
        count += lsi.values().iterator().next() - start + 1;   // hi = smallest last-seen index
      }
    }
    return count;
  }

  // B.
  static int distinctAtMostDiff(int[] nums, int k) {
    return distinctAtMost(nums, k) - distinctAtMost(nums, k - 1);
  }

  static int distinctAtMost(int[] nums, int k) {
    if (k < 0) return 0;
    Map<Integer, Integer> freq = new HashMap<>();
    int left = 0, count = 0;
    for (int right = 0; right < nums.length; right++) {
      freq.merge(nums[right], 1, Integer::sum);
      while (freq.size() > k) {
        if (freq.merge(nums[left], -1, Integer::sum) == 0) freq.remove(nums[left]);
        left++;
      }
      count += right - left + 1;
    }
    return count;
  }

  // ---------- LC 3306 ----------

  // A, as written in practice (correct; int overflows at n = 2e5 -- see vowelsRange).
  static int vowelsOriginal(String word, int k) {
    int[] lsi = new int[5];                       // last seen indices for vowels
    Arrays.fill(lsi, -1);
    Deque<Integer> cc = new LinkedList<>();       // consonant indices
    int start = 0, end = 0, count = 0;
    while (end < word.length()) {
      int idx = "aeiou".indexOf(word.charAt(end));
      if (idx < 0) cc.addLast(end);
      else lsi[idx] = end;
      end++;
      if (cc.size() > k) {
        start = cc.pollFirst() + 1;
        for (int i = 0; i < 5; i++) {
          if (lsi[i] < start) lsi[i] = -1;
        }
      }
      if (cc.size() == k && isValid(lsi)) {
        count += (min(lsi, cc.peekFirst()) - start + 1);
      }
    }
    return count;
  }

  private static int min(int[] lsi, Integer ci) {
    int m = Integer.MAX_VALUE;
    for (int i = 0; i < 5; i++) m = Math.min(m, lsi[i]);
    return ci == null ? m : Math.min(m, ci);
  }

  private static boolean isValid(int[] lsi) {
    for (int i = 0; i < 5; i++) if (lsi[i] == -1) return false;
    return true;
  }

  // A. hi = min(oldest vowel last-seen, first consonant in the window): the substring must
  // contain every vowel AND all k consonants. Returns long: the answer can reach ~n^2/2.
  // Simplified from the first version: a stale or unseen vowel index is < start, so a
  // single hi >= start check replaces the reset-to--1 loop and isValid().
  static long vowelsRange(String word, int k) {
    int[] lsi = new int[5];
    Arrays.fill(lsi, -1);
    Deque<Integer> consonants = new ArrayDeque<>();
    int start = 0;
    long count = 0;
    for (int end = 0; end < word.length(); end++) {
      int v = "aeiou".indexOf(word.charAt(end));
      if (v < 0) consonants.addLast(end);
      else lsi[v] = end;
      if (consonants.size() > k) {
        start = consonants.pollFirst() + 1;
      }
      int hi = Math.min(lsi[0], Math.min(lsi[1], Math.min(lsi[2], Math.min(lsi[3], lsi[4]))));
      if (!consonants.isEmpty()) hi = Math.min(hi, consonants.peekFirst());
      if (consonants.size() == k && hi >= start) {
        count += hi - start + 1;
      }
    }
    return count;
  }

  // B. atMost over consonants; the "contains every vowel" condition caps the start at the
  // oldest vowel last-seen, so valid starts are [left, minVowel] instead of [left, right].
  static long vowelsAtMostDiff(String word, int k) {
    return vowelsAtMost(word, k) - vowelsAtMost(word, k - 1);
  }

  static long vowelsAtMost(String word, int k) {
    if (k < 0) return 0;
    int[] lsi = new int[5];
    Arrays.fill(lsi, -1);
    int left = 0, consonants = 0;
    long count = 0;
    for (int right = 0; right < word.length(); right++) {
      int v = "aeiou".indexOf(word.charAt(right));
      if (v < 0) consonants++;
      else lsi[v] = right;
      while (consonants > k) {
        if ("aeiou".indexOf(word.charAt(left)) < 0) consonants--;
        left++;
      }
      int hi = Math.min(lsi[0], Math.min(lsi[1], Math.min(lsi[2], Math.min(lsi[3], lsi[4]))));
      if (hi >= left) count += hi - left + 1;
    }
    return count;
  }

  // ---------- Helpers and tests ----------

  private static int min(Map<Integer, Integer> lsi) {
    int m = Integer.MAX_VALUE;
    for (int v : lsi.values()) m = Math.min(m, v);
    return m;
  }

  static long bruteDistinct(int[] a, int k) {
    long c = 0;
    for (int i = 0; i < a.length; i++) {
      Set<Integer> s = new HashSet<>();
      for (int j = i; j < a.length; j++) {
        s.add(a[j]);
        if (s.size() == k) c++;
      }
    }
    return c;
  }

  static long bruteVowels(String w, int k) {
    long c = 0;
    for (int i = 0; i < w.length(); i++) {
      int mask = 0, cons = 0;
      for (int j = i; j < w.length(); j++) {
        int v = "aeiou".indexOf(w.charAt(j));
        if (v < 0) cons++;
        else mask |= 1 << v;
        if (mask == 31 && cons == k) c++;
      }
    }
    return c;
  }

  public static void main(String[] args) {
    int[] counter = {1, 2, 1, 1, 3};
    System.out.println("992 [1,2,1,1,3] k=2: original=" + distinctOriginal(counter, 2)
        + " range=" + distinctRange(counter, 2) + " atMost=" + distinctAtMostDiff(counter, 2)
        + " brute=" + bruteDistinct(counter, 2));

    Random r = new Random(1);
    int bad992 = 0, bad3306 = 0;
    for (int it = 0; it < 20000; it++) {
      int n = 1 + r.nextInt(12);
      int[] a = new int[n];
      for (int i = 0; i < n; i++) a[i] = 1 + r.nextInt(4);
      int k = 1 + r.nextInt(4);
      long b = bruteDistinct(a, k);
      if (distinctRange(a, k) != b || distinctAtMostDiff(a, k) != b) bad992++;

      StringBuilder sb = new StringBuilder();
      for (int i = 0; i < 1 + r.nextInt(14); i++) sb.append("aeioubc".charAt(r.nextInt(7)));
      int kc = r.nextInt(4);
      long bv = bruteVowels(sb.toString(), kc);
      if (vowelsOriginal(sb.toString(), kc) != bv || vowelsRange(sb.toString(), kc) != bv || vowelsAtMostDiff(sb.toString(), kc) != bv) bad3306++;
    }
    System.out.println("mismatches vs brute: 992=" + bad992 + " 3306=" + bad3306);

    String big = "aeiou".repeat(40000);              // n = 2e5: answer overflows int
    System.out.println("3306 n=2e5 k=0: range=" + vowelsRange(big, 0) + " atMost=" + vowelsAtMostDiff(big, 0));
  }
}
