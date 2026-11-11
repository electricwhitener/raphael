// Quick sort: pick a pivot (here the last value), move the smaller values to its left,
// put the pivot after them, then sort each side the same way.
public class QuickSort {
    static void sort(int[] arr, int lo, int hi) {
        if (lo >= hi) {
            return;
        }
        int p = partition(arr, lo, hi);
        sort(arr, lo, p - 1);
        sort(arr, p + 1, hi);
    }

    static int partition(int[] arr, int lo, int hi) {
        int pivot = arr[hi];
        int i = lo;                    // where the next small value goes
        for (int j = lo; j < hi; j++) {
            if (arr[j] < pivot) {
                swap(arr, i, j);
                i++;
            }
        }
        swap(arr, i, hi);              // the pivot lands in its final place
        return i;
    }

    static void swap(int[] arr, int a, int b) {
        int tmp = arr[a];
        arr[a] = arr[b];
        arr[b] = tmp;
    }

    public static void main(String[] args) {
        int[] arr = {10, 80, 30, 90, 40, 50, 70};
        sort(arr, 0, arr.length - 1);
        System.out.println(java.util.Arrays.toString(arr));
    }
}
