// Insertion sort: take the next value, and slide it left past every bigger value.
// Everything left of i is always sorted.
public class InsertionSort {
    public static void main(String[] args) {
        int[] arr = {7, 3, 9, 1, 5, 2};
        for (int i = 1; i < arr.length; i++) {
            int key = arr[i];          // the value being placed
            int j = i - 1;
            while (j >= 0 && arr[j] > key) {
                arr[j + 1] = arr[j];   // shift the bigger value right
                j--;
            }
            arr[j + 1] = key;
        }
        System.out.println(java.util.Arrays.toString(arr));
    }
}
