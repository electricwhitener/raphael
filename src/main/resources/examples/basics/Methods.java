// A method takes parameters, does its work and returns a value to the line that called it.
// Watch the call stack: each call gets its own box of variables, gone when it returns.
public class Methods {
    static int square(int x) {
        return x * x;
    }

    static int sumOfSquares(int a, int b) {
        int first = square(a);                   // moment: sumOfSquares waits while square({a}) runs
        int second = square(b);
        return first + second;
    }

    public static void main(String[] args) {
        int result = sumOfSquares(3, 4);
        System.out.println("3² + 4² = " + result);
    }
}
