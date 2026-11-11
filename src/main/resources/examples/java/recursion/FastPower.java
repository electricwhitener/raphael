// Fast exponentiation: x^n = (x^(n/2))², times one more x when n is odd. It needs about
// log n calls instead of n. Watch n halve on the way in, and the answer square on the way out.
public class FastPower {
    static long power(long x, int n) {
        if (n == 0) {
            return 1;
        }
        long half = power(x, n / 2);
        if (n % 2 == 0) {
            return half * half;
        }
        return half * half * x;                  // moment: n = {n} is odd, so one more x
    }

    public static void main(String[] args) {
        System.out.println("3^13 = " + power(3, 13));
    }
}
