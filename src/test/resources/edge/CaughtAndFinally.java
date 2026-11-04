// expect: FINISHED
// output: caught: too small
// output: finally
// output: after
class TooSmall extends Exception {
    TooSmall(String message) {
        super(message);
    }
}

public class Main {
    static void check(int n) throws TooSmall {
        if (n < 10) {
            throw new TooSmall("too small");
        }
    }

    public static void main(String[] args) {
        try {
            check(3);
            System.out.println("not reached");
        } catch (TooSmall e) {
            System.out.println("caught: " + e.getMessage());
        } finally {
            System.out.println("finally");
        }
        try {
            Object o = "text";
            Integer i = (Integer) o;
        } catch (ClassCastException | NullPointerException e) {
            System.out.println("after");
        }
    }
}
