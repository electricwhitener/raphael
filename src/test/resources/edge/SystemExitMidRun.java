// expect: FINISHED
// output: before
// no-output: after
public class Main {
    public static void main(String[] args) {
        System.out.println("before");
        System.exit(3);
        System.out.println("after");
    }
}
