import shapes.Circle;
import shapes.Shape;
import shapes.Square;

// A program in four files: the interface and the two shapes are in the shapes folder (their
// package), and Main uses them. Each call to area() runs the code of the shape it's called on.
public class Main {
    public static void main(String[] args) {
        Shape[] shapes = {new Circle(1), new Square(2)};
        double total = 0;
        for (Shape shape : shapes) {
            double area = shape.area();
            System.out.printf("%s: %.2f%n", shape.name(), area);
            total += area;
        }
        System.out.printf("total: %.2f%n", total);
    }
}
