// Binary search, recursively: look at the middle, then search only the half that can hold
// the target. Each call gets a range half as big; an empty range means it isn't there.
public class RecursiveBinarySearch {
    static int search(int[] a, int target, int lo, int hi) {
        if (lo > hi) {
            return -1;
        }
        int mid = (lo + hi) / 2;
        if (a[mid] == target) {
            return mid;
        }
        if (a[mid] < target) {
            return search(a, target, mid + 1, hi); // moment: {a[mid]} is too small, so only the right half is left
        }
        return search(a, target, lo, mid - 1);
    }

    public static void main(String[] args) {
        int[] sorted = {3, 9, 14, 21, 30, 42, 57};
        System.out.println("42 is at " + search(sorted, 42, 0, sorted.length - 1));
    }
}
