// expect: FINISHED
// output: three items
// output: a
// output: b
public class Main {
    static int count(int... items) {
        return items.length;
    }

    public static void main(String[] args) {
        var n = count(4, 5, 6);
        String word = n == 3 ? "three" : "other";
        String block = """
                a
                b
                """;
        Object o = word;
        if (o instanceof String s && !s.isEmpty()) {
            System.out.println(s + " items");
        }
        System.out.print(block);
    }
}
