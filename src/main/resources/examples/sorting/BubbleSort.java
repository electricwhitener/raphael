public class BubbleSort {
    public static void main(String[] args) {
        int[] arr = {5, 2, 9, 1, 6};
        for (int i = 0; i < arr.length - 1; i++) {
            for (int j = 0; j < arr.length - 1 - i; j++) {
                if (arr[j] > arr[j + 1]) {
                    // swap the two neighbours
                    int tmp = arr[j];
                    arr[j] = arr[j + 1];
                    arr[j + 1] = tmp;
                }
            }
        }
        System.out.println(java.util.Arrays.toString(arr));
    }
}
