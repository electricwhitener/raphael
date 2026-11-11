// BUG: countdown() should stop at 0, but nothing tells it to.
// Run it and watch the call stack grow until Raphael stops it.
public class MissingBaseCase {
    static void countdown(int n) {
        System.out.println(n);
        countdown(n - 1);              // the base case is missing
    }

    public static void main(String[] args) {
        countdown(3);
    }
}
