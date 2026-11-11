// Binary search: in a sorted array, look at the middle value and throw away the half that
// can't hold the target. Ten values take at most four looks.
public class BinarySearch {
    public static void main(String[] args) {
        int[] arr = {2, 5, 8, 12, 16, 23, 38, 56, 72, 91};
        int target = 23;
        int lo = 0;
        int hi = arr.length - 1;
        while (lo <= hi) {
            int mid = (lo + hi) / 2;
            if (arr[mid] == target) {
                System.out.println(target + " is at index " + mid);
                return;
            } else if (arr[mid] < target) {
                lo = mid + 1;          // it can only be to the right
            } else {
                hi = mid - 1;          // it can only be to the left
            }
        }
        System.out.println(target + " isn't there");
    }
}
