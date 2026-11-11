// Generics: Box<T> is written once and holds any type. Box<String> only takes Strings,
// and the compiler checks it, so no casts are needed.
public class Generics {
    static class Box<T> {
        private T item;

        void put(T item) {
            this.item = item;
        }

        T get() {
            return item;
        }
    }

    static class Pair<A, B> {
        A first;
        B second;

        Pair(A first, B second) {
            this.first = first;
            this.second = second;
        }
    }

    public static void main(String[] args) {
        Box<String> words = new Box<>();
        words.put("hello");
        Box<Integer> numbers = new Box<>();
        numbers.put(42);
        Pair<String, Integer> age = new Pair<>("Mia", 19);
        System.out.println(words.get() + ", " + numbers.get() + ", " + age.first + " is " + age.second);
    }
}
