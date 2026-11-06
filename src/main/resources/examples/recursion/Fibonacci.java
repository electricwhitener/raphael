// fib(n) = fib(n-1) + fib(n-2). Each call makes two more, so the same values are worked out
// again and again: calls[n] counts how often fib(n) runs. (The DP version stores each one once.)
public class Fibonacci {
    static int[] calls = new int[6];

    static int fib(int n) {
        calls[n]++;
        if (n < 2) {
            return n;
        }
        return fib(n - 1) + fib(n - 2);
    }

    public static void main(String[] args) {
        System.out.println("fib(5) = " + fib(5));
        System.out.println("fib(1) was worked out " + calls[1] + " times");
    }
}
