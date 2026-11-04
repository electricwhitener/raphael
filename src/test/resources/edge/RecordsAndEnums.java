// expect: FINISHED
// output: Point[x=1, y=2]
// output: MEDIUM is 2
record Point(int x, int y) {
    Point {
        if (x < 0) {
            throw new IllegalArgumentException("x < 0");
        }
    }
}

enum Size {
    SMALL(1), MEDIUM(2), LARGE(3);

    private final int weight;

    Size(int weight) {
        this.weight = weight;
    }

    int weight() {
        return weight;
    }
}

public class Main {
    public static void main(String[] args) {
        Point p = new Point(1, 2);
        Size s = Size.MEDIUM;
        String text = switch (s) {
            case SMALL -> "small";
            case MEDIUM -> "MEDIUM is " + s.weight();
            case LARGE -> "large";
        };
        System.out.println(p);
        System.out.println(text);
    }
}
