// Rotate right by k: each round, the last value goes to the front and the rest shift right.
public class RotateArray {
    public static void main(String[] args) {
        int[] arr = {1, 2, 3, 4, 5};
        int k = 2;
        for (int round = 0; round < k; round++) {
            int last = arr[arr.length - 1];
            for (int i = arr.length - 1; i > 0; i--) {
                arr[i] = arr[i - 1];
            }
            arr[0] = last;
        }
        System.out.println(java.util.Arrays.toString(arr));
    }
}
