#include <iostream>
#include <vector>
using namespace std;

// area() is virtual, so shape->area() runs the Circle's or the Square's own area,
// even though the vector holds them all as Shape pointers.
class Shape {
public:
    virtual double area() const = 0;
    virtual ~Shape() {}
};

class Circle : public Shape {
    double r;
public:
    Circle(double r) : r(r) {}
    double area() const override { return 3.14 * r * r; }
};

class Square : public Shape {
    double side;
public:
    Square(double side) : side(side) {}
    double area() const override { return side * side; }
};

int main() {
    vector<Shape *> shapes = {new Circle(1), new Square(2)};
    double total = 0;
    for (Shape *shape : shapes) {
        total += shape->area();
    }
    cout << "total area: " << total << endl;
    for (Shape *shape : shapes) {
        delete shape;
    }
    return 0;
}
