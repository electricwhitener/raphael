// Linear search: look at each value in turn until the target turns up.
public class LinearSearch {
    public static void main(String[] args) {
        int[] arr = {14, 7, 22, 9, 31, 5};
        int target = 31;
        int found = -1;
        for (int i = 0; i < arr.length; i++) {
            if (arr[i] == target) {
                found = i;
                break;
            }
        }
        System.out.println(found >= 0 ? target + " is at index " + found : target + " isn't there");
    }
}
