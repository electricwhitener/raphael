// A class is a blueprint. Each "new" makes another object, with its own copy of the fields.
public class ClassesAndObjects {
    static class Point {
        int x;
        int y;

        Point(int x, int y) {
            this.x = x;    // this.x is the field, x is the parameter
            this.y = y;
        }

        void move(int dx, int dy) {
            x += dx;
            y += dy;
        }
    }

    public static void main(String[] args) {
        Point a = new Point(1, 2);
        Point b = new Point(5, 5);
        a.move(3, 0);      // only a changes
        System.out.println("a = (" + a.x + ", " + a.y + ")");
        System.out.println("b = (" + b.x + ", " + b.y + ")");
    }
}
