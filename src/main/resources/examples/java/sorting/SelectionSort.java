// Selection sort: find the smallest value that's left, and swap it to the front.
public class SelectionSort {
    public static void main(String[] args) {
        int[] arr = {29, 10, 14, 37, 13, 5};
        for (int i = 0; i < arr.length - 1; i++) {
            int min = i;               // where the smallest value so far is
            for (int j = i + 1; j < arr.length; j++) {
                if (arr[j] < arr[min]) {
                    min = j;
                }
            }
            // swap it into place i
            int tmp = arr[i];
            arr[i] = arr[min];
            arr[min] = tmp;
        }
        System.out.println(java.util.Arrays.toString(arr));
    }
}
