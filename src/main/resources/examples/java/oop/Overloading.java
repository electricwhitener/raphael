// Overloading: several methods share one name. Java picks one by the number and types
// of the arguments, while compiling. Each result says which add() ran.
public class Overloading {
    static String add(int a, int b) {
        return "add(int, int) = " + (a + b);
    }

    static String add(int a, int b, int c) {
        return "add(int, int, int) = " + (a + b + c);
    }

    static String add(double a, double b) {
        return "add(double, double) = " + (a + b);
    }

    static String add(String a, String b) {
        return "add(String, String) = " + a + b;
    }

    public static void main(String[] args) {
        String[] results = new String[4];
        results[0] = add(1, 1);
        results[1] = add(1, 2, 3);
        results[2] = add(0.25, 0.25);
        results[3] = add("over", "load");
        for (String r : results) {
            System.out.println(r);
        }
    }
}
