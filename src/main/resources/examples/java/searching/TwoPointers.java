// Two pointers: find two values that add up to target in a sorted array. Too small? Move
// the left one right. Too big? Move the right one left.
public class TwoPointers {
    public static void main(String[] args) {
        int[] arr = {1, 3, 4, 6, 8, 11, 15};
        int target = 14;
        int i = 0;
        int j = arr.length - 1;
        while (i < j) {
            int sum = arr[i] + arr[j];
            if (sum == target) {
                System.out.println(arr[i] + " + " + arr[j] + " = " + target);
                return;
            } else if (sum < target) {
                i++;
            } else {
                j--;
            }
        }
        System.out.println("no pair adds up to " + target);
    }
}
