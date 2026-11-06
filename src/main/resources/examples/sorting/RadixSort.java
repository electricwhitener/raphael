// Radix sort: sort by the last digit, then by the tens, then by the hundreds. Each pass keeps
// the order of the one before for equal digits, so the earlier passes still count.
public class RadixSort {
    public static void main(String[] args) {
        int[] arr = {170, 45, 75, 90, 802, 24, 2, 66};
        for (int place = 1; place <= 100; place *= 10) {
            int[] count = new int[10];
            for (int v : arr) {
                count[(v / place) % 10]++;
            }
            for (int d = 1; d < 10; d++) {
                count[d] += count[d - 1];      // where each digit's values end
            }
            int[] out = new int[arr.length];
            for (int i = arr.length - 1; i >= 0; i--) {
                int digit = (arr[i] / place) % 10;
                count[digit]--;
                out[count[digit]] = arr[i];
            }
            arr = out;
        }
        System.out.println(java.util.Arrays.toString(arr));
    }
}
