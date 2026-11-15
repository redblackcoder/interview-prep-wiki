package practice.prep.libs;

import java.util.ListIterator;
import java.util.NoSuchElementException;

public class IntArrayListIterator implements ListIterator<Integer> {
    private final int[] array;
    private int cursor;      // Index of element to be returned by next()
    private int lastRet = -1; // Index of element last returned; -1 if no such element

    public IntArrayListIterator(int[] array) {
        this(array, 0);
    }

    public IntArrayListIterator(int[] array, int startIndex) {
        if (startIndex < 0 || startIndex > array.length) {
            throw new IndexOutOfBoundsException("Index: " + startIndex);
        }
        this.array = array;
        this.cursor = startIndex;
    }

    @Override
    public boolean hasNext() {
        return cursor < array.length;
    }

    @Override
    public Integer next() {
        if (!hasNext()) throw new NoSuchElementException();
        lastRet = cursor;
        return array[cursor++];
    }

    @Override
    public boolean hasPrevious() {
        return cursor > 0;
    }

    @Override
    public Integer previous() {
        if (!hasPrevious()) throw new NoSuchElementException();
        cursor--;
        lastRet = cursor;
        return array[cursor];
    }

    @Override
    public int nextIndex() {
        return cursor;
    }

    @Override
    public int previousIndex() {
        return cursor - 1;
    }

    @Override
    public void set(Integer value) {
        if (lastRet < 0) {
            throw new IllegalStateException("next() or previous() must be called before set()");
        }
        array[lastRet] = value;
    }

    @Override
    public void remove() {
        throw new UnsupportedOperationException("Cannot change primitive array size");
    }

    @Override
    public void add(Integer e) {
        throw new UnsupportedOperationException("Cannot change primitive array size");
    }
}
