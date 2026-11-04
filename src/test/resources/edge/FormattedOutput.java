// expect: FINISHED
// output: pi is 3.14
// output: [   42]
// output: abc!
public class Main {
    public static void main(String[] args) {
        System.out.printf("pi is %.2f%n", Math.PI);
        System.out.println(String.format("[%5d]", 42));
        StringBuilder sb = new StringBuilder();
        for (char c = 'a'; c <= 'c'; c++) {
            sb.append(c);
        }
        sb.append('!');
        System.out.println(sb);
    }
}
