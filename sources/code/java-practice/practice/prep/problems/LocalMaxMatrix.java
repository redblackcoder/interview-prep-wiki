package practice.prep.problems;

import java.util.*;

import practice.prep.libs.GridSliceIterator;
import practice.prep.libs.IntArrayListIterator;
import practice.prep.libs.StreamMax;
import practice.prep.libs.GridSliceIterator.Direction;

// https://leetcode.com/problems/largest-local-values-in-a-matrix
public class LocalMaxMatrix {
    public static int[][] approachFour(int[][] grid) {
        final int n = grid.length;
        int[][] temp = new int[n][n-2];
        for (int i = 0; i < n; i++) {
            GridSliceIterator gridRowIt = new GridSliceIterator(grid, i, Direction.ROW);
            StreamMax gridRowWindowMax = new StreamMax(gridRowIt, 3);
            int j = 0;
            do {
                temp[i][j++] = gridRowWindowMax.getMax();
            } while (gridRowWindowMax.advance());
        }

        int[][] maxLocal = new int[n-2][n-2];
        for (int j = 0; j < n-2; j++) {
            GridSliceIterator tempColIt = new GridSliceIterator(temp, j, Direction.COLUMN);
            StreamMax tempColWindowMax = new StreamMax(tempColIt, 3);
            int i = 0;
            do {
                maxLocal[i++][j] = tempColWindowMax.getMax();
            } while (tempColWindowMax.advance());
        }

        return maxLocal;
    }

    public static int[][] approachThree(int[][] grid) {
        PriorityQueue<Integer> maxHeap = new PriorityQueue<>(Comparator.reverseOrder());
        int n = grid.length;
        int[][] maxLocal = new int[n-2][n-2];
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                maxHeap.add(grid[i][j]);
            }
        }

        for (int row = 0; row < n-2; row++) {
            // true -> left to right; false -> right to left
            boolean direction = (row%2 == 0);
            int colS = direction ? 0 : n-3;
            int step = direction ? 1 : -1;
            int colE = (n-3) - colS + step;
            for (int col = colS; col != colE; col += step) {
                maxLocal[row][col] = maxHeap.peek();
                if ((col + step) == colE && row == n-3) {
                    continue;
                }

                updateHeap(maxHeap, grid, row, col, direction);
            }
        }

        return maxLocal;
    }

    private static void updateHeap(
            PriorityQueue<Integer> maxHeap,
            int[][] grid,
            int row,
            int col,
            boolean direction) {
        final int n = grid.length;
        if (((col == n-3) && direction) || ((col == 0) && !direction)) {
            // last column & moving left to right, for next iteration, move down
            // OR
            // first column & moving right to left, for next iteration, move down
            for (int i = col; i < col + 3; i++) {
                maxHeap.remove(grid[row][i]);
                maxHeap.add(grid[row+3][i]);
                System.out.println("Removed " + grid[row][i] + " | Added " + grid[row+3][i]);
            }
        } else {
            // moving right or left
            final int oldCol = direction ? col : (col + 2);
            final int newCol = direction ? (col + 3) : (col - 1);
            for (int i = row; i < row+3; i++) {
                maxHeap.remove(grid[i][oldCol]);
                maxHeap.add(grid[i][newCol]);
                System.out.println("Removed " + grid[i][oldCol] + " | Added " + grid[i][newCol]);
            }
        }
    }

    public static int[][] approachTwo(int[][] grid) {
        int n = grid.length;
        int[][] maxLocal = new int[n-2][n-2];
        for (int i = 0; i < n-2; i++) {
            for (int j = 0; j < n-2; j++) {
                maxLocal[i][j] = getMax(grid, i, j, 3);
            }
        }

        return maxLocal;
    }

    private static int getMax(int[][] grid, int i, int j, int s) {
        int max = grid[i][j];
        for (int ii = i; ii < i+s; ii++) {
            for (int jj = j; jj < j+s; jj++) {
                max = Math.max(max, grid[ii][jj]);
            }
        }

        return max;
    }

    public static int[][] approachOne(int[][] grid) {
        PriorityQueue<Integer> maxHeap = new PriorityQueue<>(Comparator.reverseOrder());
        int n = grid.length;
        int[][] maxLocal = new int[n-2][n-2];
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                maxHeap.add(grid[i][j]);
            }
        }

        int[] removed = new int[3];
        int[] added = new int[3];
        for (int i = 0; i < n-2; i++) {
            if (i%2 == 0) {
                for (int j = 0; j < n-2; j++) {
                    maxLocal[i][j] = maxHeap.peek();
                    boolean updated = false;
                    if (j < n-3) {
                        // prepare for next one in the same row
                        for (int k = i; k < i+3; k++) {
                            maxHeap.remove(grid[k][j]);
                            maxHeap.add(grid[k][j+3]);

                            removed[k-i] = grid[k][j];
                            added[k-i] = grid[k][j+3];
                            updated = true;
                        }
                    } else if (i < n-3) {
                        // prepare for next one in the row below
                        for (int k = j; k < j+3; k++) {
                            maxHeap.remove(grid[i][k]);
                            maxHeap.add(grid[i+3][k]);

                            removed[k-j] = grid[i][k];
                            added[k-j] = grid[i+3][k];
                            updated = true;
                        }
                    }

                    if (updated) {
                        System.out.println("removed: " + Arrays.toString(removed));
                        System.out.println("added: " + Arrays.toString(added));
                    }
                }
            } else {
                for (int j = n-3; j >= 0; j--) {
                    maxLocal[i][j] = maxHeap.peek();
                    boolean updated = false;
                    if (j > 0) {
                        // prepare for next one in the same row
                        for (int k = i; k < i+3; k++) {
                            maxHeap.remove(grid[k][j+2]);
                            maxHeap.add(grid[k][j-1]);

                            removed[k-i] = grid[k][j+2];
                            added[k-i] = grid[k][j-1];
                            updated = true;
                        }
                    } else if (i < n-3) {
                        // prepare for next one in the row below
                        for (int k = j; k < j+3; k++) {
                            maxHeap.remove(grid[i][k]);
                            maxHeap.add(grid[i+3][k]);

                            removed[k-j] = grid[i][k];
                            added[k-j] = grid[i+3][k];
                            updated = true;
                        }
                    }

                    if (updated) {
                        System.out.println("removed: " + Arrays.toString(removed));
                        System.out.println("added: " + Arrays.toString(added));
                    }
                }
            }
        }

        return maxLocal;
    }
}
