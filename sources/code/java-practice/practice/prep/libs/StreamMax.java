package practice.prep.libs;

import java.util.Deque;
import java.util.LinkedList;
import java.util.ListIterator;

// Given a stream of integers (size >> K), return the maximum over window of K elements
// 7, 4, 1, 8, 10, 1, 3, 4; k = 3
// tail -> 1, 4, 7 -> head (7, 4, 1)
// tail -> 8 -> head (4, 1, 8)
// tail -> 10 -> head (1, 8, 10)
// tail -> 1, 10 -> head (8, 10, 1)
// tail -> 3, 10 -> head (10, 1, 3)
// tail -> 4 -> head (1, 3, 4)
// head always poins to the max
public class StreamMax {
    Deque<int[]> queue;
    ListIterator<Integer> stream;
    int windowSize;
    
    public StreamMax(ListIterator<Integer> stream, int windowSize) {
        this.stream = stream;
        this.windowSize = windowSize;
        queue = new LinkedList<>();
        init();
    }

    private void init() {
        int first = stream.next();
        queue.addLast(new int[] {first, 0});
        for (int i = 1; i < windowSize; i++) {
            addLast(stream.next(), i);
        }
    }

    public int getMax() {
        return queue.peekFirst()[0];
    }

    public boolean advance() {
        if (!stream.hasNext()) {
            return false;
        }

        int nextIdx = stream.nextIndex();
        int nextVal = stream.next();
        addLast(nextVal, nextIdx);

        int leavingIdx = nextIdx - windowSize;
        if (queue.peekFirst()[1] == leavingIdx) {
            queue.removeFirst();
        }

        return true;
    }

    private void addLast(int val, int idx) {
        while (queue.peekLast() != null && queue.peekLast()[0] <= val) {
            queue.removeLast();
        }

        queue.addLast(new int[] {val, idx});
    }
}
