public class FibDP {
    public static void main(String[] args) {
        int n = 10;
        long[] dp = new long[n + 1];
        dp[0] = 0;
        dp[1] = 1;
        for (int i = 2; i <= n; i++) {
            dp[i] = dp[i - 1] + dp[i - 2];
        }
        System.out.println("fib(" + n + ") = " + dp[n]);
    }
}
