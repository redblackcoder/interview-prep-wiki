package practice.prep.problems;

public class SubStringsRemoval {
    public String removeOccurrences(String s, String part) {
        int n = s.length();
        int[] jumps = new int[n + 1];
        for (int i = 0; i < n + 1; i++) {
            jumps[i] = i;
        }

        helper(s, part, jumps, 0);
        return build(s, jumps, 0);
    }

    private void helper(String s, String part, int[] jumps, int i) {
        int n = s.length();
        int m = part.length();
        while (i < n && s.charAt(i) != part.charAt(0)) {
            i++;
        }

        if (i >= n) {
            return;
        }

        int j = 0;
        while (i + j < n && j < m && s.charAt(i + j) == part.charAt(j)) {
            j++;
        }

        if (j == m) {
            // full match
            jumps[i] += m;
            helper(s, part, jumps, jumps[i]);
        } else {
            // partial match, solve the rest
            helper(s, part, jumps, i + j);
        }

        // match after solving rest
        j = 0;
        int si = i;
        while (j < m && si < n && s.charAt(si) == part.charAt(j)) {
            j++;
            si = jumps[si + 1];
        }

        if (j == m) {
            // found full match, collapse
            jumps[i] = si;
        }
    }

    private String build(String s, int[] jumps, int i) {
        StringBuilder sb = new StringBuilder();
        int next = jumps[i];
        while (next != s.length()) {
            sb.append(s.charAt(next));
            next = jumps[next + 1];
        }

        return sb.toString();
    }

    private String simple(String s, String part) {
        String sub = s;
        do {
            s = sub;
            sub = s.replaceFirst(part, "");
        } while (!sub.equals(s));

        return sub;
    }    
}
