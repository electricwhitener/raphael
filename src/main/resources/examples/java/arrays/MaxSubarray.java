// Kadane's algorithm: the largest sum of a run of neighbours. At each index, either extend
// the run ending before it, or start again here, whichever is bigger.
public class MaxSubarray {
    public static void main(String[] args) {
        int[] arr = {-2, 1, -3, 4, -1, 2, 1, -5, 4};
        int here = arr[0];             // the best sum ending at i
        int best = arr[0];
        for (int i = 1; i < arr.length; i++) {
            here = Math.max(arr[i], here + arr[i]);
            best = Math.max(best, here);
        }
        System.out.println("largest sum: " + best);
    }
}
