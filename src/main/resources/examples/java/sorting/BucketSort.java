// Bucket sort: drop each value into a bucket for its range (0-9, 10-19, …), sort each small
// bucket, then read the buckets out in order.
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class BucketSort {
    public static void main(String[] args) {
        int[] arr = {29, 3, 41, 18, 7, 35, 12, 44};
        List<List<Integer>> buckets = new ArrayList<>();
        for (int b = 0; b < 5; b++) {
            buckets.add(new ArrayList<>());
        }
        for (int v : arr) {
            buckets.get(v / 10).add(v);
        }
        int k = 0;
        for (List<Integer> bucket : buckets) {
            Collections.sort(bucket);
            for (int v : bucket) {
                arr[k++] = v;
            }
        }
        System.out.println(java.util.Arrays.toString(arr));
    }
}
