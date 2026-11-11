// toString() decides how an object prints; equals() decides when two objects count as equal.
// Without your own equals(), Java only calls an object equal to itself (the same as ==).
public class ObjectEquality {
    static class Point {
        private final int x;
        private final int y;

        Point(int x, int y) {
            this.x = x;
            this.y = y;
        }

        @Override
        public String toString() {
            return "(" + x + ", " + y + ")";
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof Point)) {
                return false;
            }
            Point p = (Point) other;
            return x == p.x && y == p.y;         // moment: equals() compares the fields, not the references
        }

        @Override
        public int hashCode() {
            return 31 * x + y;
        }
    }

    public static void main(String[] args) {
        Point a = new Point(2, 3);
        Point b = new Point(2, 3);
        Point c = a;
        boolean sameObject = a == b;             // moment: a and b are two different objects, so == is false
        boolean sameValue = a.equals(b);
        boolean alias = a == c;                  // moment: c holds the same reference as a, so == is true
        System.out.println(a + " " + sameObject + " " + sameValue + " " + alias);
    }
}
