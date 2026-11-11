// Merge sort: split the array in half, sort each half the same way, then merge the two
// sorted halves by always taking the smaller front value.
public class MergeSort {
    static void sort(int[] arr, int lo, int hi) {
        if (lo >= hi) {
            return;                    // one value is already sorted
        }
        int mid = (lo + hi) / 2;
        sort(arr, lo, mid);
        sort(arr, mid + 1, hi);
        merge(arr, lo, mid, hi);
    }

    static void merge(int[] arr, int lo, int mid, int hi) {
        int[] tmp = new int[hi - lo + 1];
        int i = lo;                    // front of the left half
        int j = mid + 1;               // front of the right half
        int k = 0;
        while (i <= mid && j <= hi) {
            if (arr[i] <= arr[j]) {
                tmp[k++] = arr[i++];
            } else {
                tmp[k++] = arr[j++];
            }
        }
        while (i <= mid) {             // copy whatever is left
            tmp[k++] = arr[i++];
        }
        while (j <= hi) {
            tmp[k++] = arr[j++];
        }
        for (k = 0; k < tmp.length; k++) {
            arr[lo + k] = tmp[k];
        }
    }

    public static void main(String[] args) {
        int[] arr = {38, 27, 43, 3, 9, 82, 10};
        sort(arr, 0, arr.length - 1);
        System.out.println(java.util.Arrays.toString(arr));
    }
}
