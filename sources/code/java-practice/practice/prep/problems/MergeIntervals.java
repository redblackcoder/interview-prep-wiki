package practice.prep.problems;

import java.util.ArrayList;
import java.util.List;

public class MergeIntervals {
    public static class Interval {
        public final int start;
        public final int end;

        public Interval(int start, int end) {
            this.start = start;
            this.end = end;
        }

        @Override
        public String toString() {
            return String.format("[%d, %d]", start, end);
        }
    }

    public List<Interval> union(List<Interval> a, List<Interval> b) {
        int i = 0;
        int j = 0;
        Interval temp = null;
        List<Interval> result = new ArrayList<>();

        while (i < a.size() || j < b.size()) {
            Interval next;
            if (i == a.size()) {
                next = b.get(j++);
            } else if (j == b.size()) {
                next = a.get(i++);
            } else if (isSmaller(a.get(i), b.get(j))) {
                next = a.get(i++);
            } else {
                next = b.get(j++);
            }

            if (temp == null) {
                temp = next;
            } else {
                if (doOverlap(temp, next)) {
                    temp = merge(temp, next);
                } else {
                    result.add(temp);
                    temp = next;
                }
            }
        }

        if (temp != null) {
            result.add(temp);
        }

        return result;
    }

    // return true if a smaller than b
    private static boolean isSmaller(Interval a, Interval b) {
        return a.start <= b.start;
    }

    private static boolean doOverlap(Interval a, Interval b) {
        // a.start, a.end
        // b.start, b.end
        if (isSmaller(a, b)) {
            return a.end >= b.start;
        } else {
            return b.end >= a.start;
        }
    }

    private static Interval merge(Interval a, Interval b) {
        // a is smaller
        return new Interval(a.start, Math.max(a.end, b.end));
    }
}
