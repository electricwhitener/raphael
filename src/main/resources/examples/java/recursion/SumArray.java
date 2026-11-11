// The sum of an array, recursively: the sum from index i is a[i] plus the sum of the rest.
// The base case is past the end, where the sum is 0. Watch the values come back down the stack.
public class SumArray {
    static int sum(int[] a, int i) {
        if (i == a.length) {
            return 0;
        }
        return a[i] + sum(a, i + 1);
    }

    public static void main(String[] args) {
        int[] values = {5, 2, 8, 1};
        System.out.println("sum = " + sum(values, 0));
    }
}
