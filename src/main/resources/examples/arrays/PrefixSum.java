// prefix[i] is the sum of the first i values. Then the sum of any range is one subtraction,
// however long the range is.
public class PrefixSum {
    public static void main(String[] args) {
        int[] arr = {3, 1, 4, 1, 5, 9};
        int[] prefix = new int[arr.length + 1];
        for (int i = 0; i < arr.length; i++) {
            prefix[i + 1] = prefix[i] + arr[i];
        }
        // the sum of arr[2..4]
        int sum = prefix[5] - prefix[2];
        System.out.println("4 + 1 + 5 = " + sum);
    }
}
