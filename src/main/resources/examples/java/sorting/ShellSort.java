// Shell sort: insertion sort on values gap apart. Big gaps move values far in one go;
// the last pass, with gap 1, is a plain insertion sort on an almost sorted array.
public class ShellSort {
    public static void main(String[] args) {
        int[] arr = {23, 12, 1, 8, 34, 54, 2, 3};
        for (int gap = arr.length / 2; gap > 0; gap /= 2) {
            for (int i = gap; i < arr.length; i++) {
                int key = arr[i];
                int j = i;
                while (j >= gap && arr[j - gap] > key) {
                    arr[j] = arr[j - gap];
                    j -= gap;
                }
                arr[j] = key;
            }
        }
        System.out.println(java.util.Arrays.toString(arr));
    }
}
