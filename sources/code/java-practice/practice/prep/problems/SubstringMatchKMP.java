package practice.prep.problems;

import java.util.*;

// given a word and a pattern, find all occurences of the pattern in the word
public class SubstringMatchKMP {
    private final String pattern;

    // pattern length
    private final int m;

    // LPS (Longest Prefix Suffix) table aka Failure Function
    private final int[] ff;

    public SubstringMatchKMP(String pattern) {
        assert pattern != null;
        assert pattern.length() >= 1;

        this.pattern = pattern;
        this.m = pattern.length();
        this.ff = new int[m];
        buildFF();
    }

    // returns the starting indices of the matches found
    public List<Integer> matches(String word) {
        assert word != null;
        assert word.length() >= 1;

        int i = 0;
        int j = 0;
        List<Integer> result = new ArrayList<>();
        while (i < word.length()) {
            if (word.charAt(i) == pattern.charAt(j)) {
                i++;
                j++;
                if (j == m) {
                    // found a match
                    result.add(i - m);

                    // reset j to find next match
                    j = ff[j - 1];
                }
            } else {
                if (j == 0) {
                    i++;
                } else {
                    j = ff[j - 1];
                }
            }
        }

        return result;
    }

    private void buildFF() {
        ff[0] = 0;
        for (int i = 1; i < m; i++) {
            int l = ff[i - 1];
            if (pattern.charAt(l) == pattern.charAt(i)) {
                ff[i] = l + 1;
            } else {
                while (l > 0) {
                    l = ff[l - 1];
                    if (pattern.charAt(l) == pattern.charAt(i)) {
                        ff[i] = l + 1;
                        break;
                    }
                }
            }
        }
    }
}
