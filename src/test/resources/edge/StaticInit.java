// expect: FINISHED
// output: count 3
class Counter {
    static int count;
    static final int START;

    static {
        START = 1;
        count = START;
    }

    Counter() {
        count++;
    }
}

public class Main {
    public static void main(String[] args) {
        new Counter();
        new Counter();
        System.out.println("count " + Counter.count);
    }
}
