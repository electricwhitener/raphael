// Recursion: factorial(4) = 4 * factorial(3), and so on down to the base case, factorial(1).
// Watch the calls pile up on the call stack, then return one by one.
public class Factorial {
    static int factorial(int n) {
        if (n <= 1) {
            return 1;                  // the base case stops the recursion
        }
        return n * factorial(n - 1);
    }

    public static void main(String[] args) {
        System.out.println("4! = " + factorial(4));
    }
}
