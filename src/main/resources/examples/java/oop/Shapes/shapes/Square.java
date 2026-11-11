package shapes;

public class Square implements Shape {
    private final double side;

    public Square(double side) {
        this.side = side;
    }

    public double area() {
        return side * side;
    }

    public String name() {
        return "square";
    }
}
