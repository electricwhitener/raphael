// Java passes a copy of each argument. A method can change the object an argument points at,
// but not the caller's variable.
public class PassByValue {
    static class Counter {
        int value;
    }

    static void addOne(int n) {
        n++;                       // changes the copy only
    }

    static void addOne(Counter c) {
        c.value++;                 // changes the object both arrows point at
    }

    static void replace(Counter c) {
        c = new Counter();         // points the copy somewhere else; the caller's stays put
        c.value = 100;
    }

    public static void main(String[] args) {
        int n = 5;
        addOne(n);
        Counter counter = new Counter();
        addOne(counter);
        replace(counter);
        System.out.println("n = " + n + ", counter = " + counter.value);
    }
}
