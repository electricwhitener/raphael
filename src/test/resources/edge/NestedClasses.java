// expect: FINISHED
// output: inner 3
// output: anonymous ran
// output: nested 4
public class Main {
    private int base = 1;

    class Inner {
        int plus(int x) {
            return base + x;
        }
    }

    static class Nested {
        int twice(int x) {
            return 2 * x;
        }
    }

    public static void main(String[] args) {
        Main outer = new Main();
        Main.Inner inner = outer.new Inner();
        System.out.println("inner " + inner.plus(2));
        Runnable job = new Runnable() {
            @Override
            public void run() {
                System.out.println("anonymous ran");
            }
        };
        job.run();
        System.out.println("nested " + new Nested().twice(2));
    }
}
