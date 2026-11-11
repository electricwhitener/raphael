// try runs until something goes wrong. catch runs only if it did. finally runs either way.
public class TryCatchFinally {
    static int divide(int a, int b) {
        try {
            int result = a / b;        // throws ArithmeticException when b is 0
            System.out.println(a + " / " + b + " = " + result);
            return result;
        } catch (ArithmeticException e) {
            System.out.println("can't divide " + a + " by zero");
            return 0;
        } finally {
            System.out.println("done with " + a + " / " + b);
        }
    }

    public static void main(String[] args) {
        divide(10, 2);
        divide(7, 0);
    }
}
