// expect: FINISHED
// output: Hi, Ada!
interface Greeter {
    String name();

    default String greet() {
        return "Hi, " + name() + "!";
    }

    static Greeter of(String name) {
        return () -> name;
    }
}

public class Main {
    public static void main(String[] args) {
        Greeter ada = Greeter.of("Ada");
        System.out.println(ada.greet());
    }
}
