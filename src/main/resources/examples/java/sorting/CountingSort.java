// Counting sort: count how often each value appears, then write the values out in order.
// It never compares two values, so it suits small whole numbers.
public class CountingSort {
    public static void main(String[] args) {
        int[] arr = {4, 2, 2, 8, 3, 3, 1};
        int[] count = new int[9];      // values are 0 to 8
        for (int v : arr) {
            count[v]++;
        }
        int k = 0;
        for (int v = 0; v < count.length; v++) {
            while (count[v] > 0) {
                arr[k++] = v;
                count[v]--;
            }
        }
        System.out.println(java.util.Arrays.toString(arr));
    }
}
