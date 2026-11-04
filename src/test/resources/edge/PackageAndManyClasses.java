// expect: FINISHED
// output: area 12.0
package shapes.demo;

abstract class Shape {
    abstract double area();
}

final class Rect extends Shape {
    private final double w, h;

    Rect(double w, double h) {
        this.w = w;
        this.h = h;
    }

    @Override
    double area() {
        return w * h;
    }
}

public class Main {
    public static void main(String[] args) {
        Shape s = new Rect(3, 4);
        System.out.println("area " + s.area());
    }
}

class Unused {
}
