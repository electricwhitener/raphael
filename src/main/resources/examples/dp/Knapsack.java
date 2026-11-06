// 0/1 knapsack: the most value that fits in a bag. Cell [i][w] is the best value using the
// first i items with room w: either skip item i, or take it and use the best for the room left.
public class Knapsack {
    public static void main(String[] args) {
        int[] weight = {1, 3, 4, 5};
        int[] value = {1, 4, 5, 7};
        int room = 7;
        int[][] best = new int[weight.length + 1][room + 1];
        for (int i = 1; i <= weight.length; i++) {
            for (int w = 0; w <= room; w++) {
                best[i][w] = best[i - 1][w];                   // skip item i
                if (weight[i - 1] <= w) {
                    int take = best[i - 1][w - weight[i - 1]] + value[i - 1];
                    best[i][w] = Math.max(best[i][w], take);   // or take it
                }
            }
        }
        System.out.println("best value: " + best[weight.length][room]);
    }
}
