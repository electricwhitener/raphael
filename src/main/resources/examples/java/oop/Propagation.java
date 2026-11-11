// An exception nobody catches leaves its method and goes to the caller, and on up the stack,
// until a catch takes it. Watch it leave divide() and average() before main catches it.
public class Propagation {
    static int divide(int a, int b) {
        return a / b;                            // moment: dividing {a} by {b}
    }

    static int average(int[] values) {
        int sum = 0;
        for (int v : values) {
            sum += v;
        }
        return divide(sum, values.length);       // with no values, that's a division by 0
    }

    public static void main(String[] args) {
        try {
            System.out.println(average(new int[] {4, 8}));
            System.out.println(average(new int[0]));
        } catch (ArithmeticException e) {
            System.out.println("main caught it: " + e.getMessage());
        }
    }
}
