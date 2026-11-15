package practice.prep.libs;

public class QuickSort {
    public static <T extends Comparable<T>> void sort(T[] arr) {
        if (arr == null || arr.length <= 1) {
            return;
        }

        sortRec(arr, 0, arr.length - 1);
    }

    private static <T extends Comparable<T>> void sortRec(T[] arr, int start, int end) {
        if (start >= end) {
            return;
        }

        int split = partition(arr, start, end);
        sortRec(arr, start, split - 1);
        sortRec(arr, split + 1, end);
    }

    private static <T extends Comparable<T>> int partition(T[] arr, int start, int end) {
        T val = arr[end];
        int low = start - 1;
        for (int i = start; i < end; i++) {
            if (arr[i].compareTo(val) < 0) {
                swap(arr, ++low, i);
            }
        }

        swap(arr, ++low, end);
        return low;
    }

    private static <T> void swap(T[] arr, int i, int j) {
        T temp = arr[i];
        arr[i] = arr[j];
        arr[j] = temp;
    }
}
