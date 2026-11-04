// expect: FINISHED
// output-length-at-least: 1000000
public class Main {
    public static void main(String[] args) {
        String line = "0123456789".repeat(1000);
        StringBuilder all = new StringBuilder();
        for (int i = 0; i < 100; i++) all.append(line).append('\n');
        System.out.print(all);
    }
}
