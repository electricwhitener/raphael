// expect: FINISHED
// output: [3, 2, 1]
import java.util.ArrayList;
import java.util.List;

class Box<T extends Comparable<T>> {
    private T value;

    Box(T value) {
        this.value = value;
    }

    T get() {
        return value;
    }

    boolean biggerThan(Box<T> other) {
        return value.compareTo(other.value) > 0;
    }
}

public class Main {
    static <E> List<E> reversed(List<E> items) {
        List<E> out = new ArrayList<>();
        for (int i = items.size() - 1; i >= 0; i--) {
            out.add(items.get(i));
        }
        return out;
    }

    public static void main(String[] args) {
        Box<Integer> a = new Box<>(5);
        Box<Integer> b = new Box<>(7);
        boolean bigger = a.biggerThan(b);
        System.out.println(reversed(List.of(1, 2, 3)));
    }
}
