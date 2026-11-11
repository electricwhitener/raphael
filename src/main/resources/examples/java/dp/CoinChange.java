// The fewest coins that make each amount from 0 to 11. The answer for an amount comes from
// an amount already solved: best[a] = best[a - coin] + 1, for the coin that gives the least.
public class CoinChange {
    public static void main(String[] args) {
        int[] coins = {1, 2, 5};
        int target = 11;
        int[] best = new int[target + 1];
        for (int a = 1; a <= target; a++) {
            best[a] = target + 1;      // more than any real answer: "not possible yet"
            for (int coin : coins) {
                if (coin <= a && best[a - coin] + 1 < best[a]) {
                    best[a] = best[a - coin] + 1;
                }
            }
        }
        System.out.println(target + " needs " + best[target] + " coins");
    }
}
