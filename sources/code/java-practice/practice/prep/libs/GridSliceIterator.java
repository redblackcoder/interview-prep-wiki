package practice.prep.libs;

import java.util.ListIterator;
import java.util.NoSuchElementException;

public class GridSliceIterator implements ListIterator<Integer> {
    public enum Direction { ROW, COLUMN }

    private final int[][] grid;
    private final int sliceIndex;
    private final Direction direction;
    private final int length;
    private int cursor = 0;
    private int lastRet = -1;

    public GridSliceIterator(int[][] grid, int sliceIndex, Direction direction) {
        this.grid = grid;
        this.sliceIndex = sliceIndex;
        this.direction = direction;
        this.length = (direction == Direction.ROW) ? grid[sliceIndex].length : grid.length;
    }

    private int getVal(int index) {
        return (direction == Direction.ROW) ? grid[sliceIndex][index] : grid[index][sliceIndex];
    }

    private void setVal(int index, int value) {
        if (direction == Direction.ROW) {
            grid[sliceIndex][index] = value;
        } else {
            grid[index][sliceIndex] = value;
        }
    }

    @Override public boolean hasNext() { return cursor < length; }
    @Override public boolean hasPrevious() { return cursor > 0; }
    @Override public int nextIndex() { return cursor; }
    @Override public int previousIndex() { return cursor - 1; }

    @Override
    public Integer next() {
        if (!hasNext()) throw new NoSuchElementException();
        lastRet = cursor;
        return getVal(cursor++);
    }

    @Override
    public Integer previous() {
        if (!hasPrevious()) throw new NoSuchElementException();
        cursor--;
        lastRet = cursor;
        return getVal(cursor);
    }

    @Override
    public void set(Integer value) {
        if (lastRet < 0) throw new IllegalStateException();
        setVal(lastRet, value);
    }

    @Override public void remove() { throw new UnsupportedOperationException(); }
    @Override public void add(Integer e) { throw new UnsupportedOperationException(); }
}
