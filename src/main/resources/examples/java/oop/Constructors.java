// A class can have several constructors. this(…) lets one call another, so the setup is written once.
public class Constructors {
    static class Rectangle {
        int width;
        int height;
        String color;

        Rectangle(int width, int height, String color) {
            this.width = width;
            this.height = height;
            this.color = color;
        }

        Rectangle(int width, int height) {
            this(width, height, "black");   // calls the constructor above
        }

        Rectangle(int side) {
            this(side, side);               // a square
        }
    }

    public static void main(String[] args) {
        Rectangle a = new Rectangle(4, 2, "red");
        Rectangle b = new Rectangle(3, 5);
        Rectangle c = new Rectangle(6);
        System.out.println(a.color + " " + b.color + " " + c.width + "x" + c.height);
    }
}
