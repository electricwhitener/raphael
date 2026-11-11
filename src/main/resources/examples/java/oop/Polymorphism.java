// Polymorphism: the array holds Shapes, and shape.area() runs the area() of each object's
// own class, chosen while the program runs.
class Shape {
    double area() {
        return 0;
    }
}

class Circle extends Shape {
    double radius;

    Circle(double radius) {
        this.radius = radius;
    }

    @Override
    double area() {
        return 3.14 * radius * radius;
    }
}

class Square extends Shape {
    double side;

    Square(double side) {
        this.side = side;
    }

    @Override
    double area() {
        return side * side;
    }
}

public class Polymorphism {
    public static void main(String[] args) {
        Shape[] shapes = {new Circle(1), new Square(2), new Circle(2)};
        double total = 0;
        for (Shape shape : shapes) {
            total += shape.area();     // the same call, a different method each time
        }
        System.out.printf("total area: %.2f%n", total);
    }
}
