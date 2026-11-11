// An interface can give a method a default body. A class that implements it gets that body
// for free, or writes its own (overrides it). Watch which version runs for each object.
public class DefaultMethods {
    interface Greeter {
        String name();

        default String greet() {
            return "Hello, " + name();
        }
    }

    static class English implements Greeter {
        public String name() {
            return "Sam";
        }
    }

    static class Punjabi implements Greeter {
        public String name() {
            return "Simran";
        }

        @Override
        public String greet() {
            return "Sat Sri Akal, " + name();    // its own greet(), instead of the default
        }
    }

    public static void main(String[] args) {
        Greeter[] people = {new English(), new Punjabi()};
        for (Greeter g : people) {
            System.out.println(g.greet());
        }
    }
}
