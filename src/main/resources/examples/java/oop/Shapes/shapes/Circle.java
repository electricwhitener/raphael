package shapes;

public class Circle implements Shape {
    private final double radius;

    public Circle(double radius) {
        this.radius = radius;
    }

    public double area() {
        return 3.14 * radius * radius;
    }

    public String name() {
        return "circle";
    }
}
