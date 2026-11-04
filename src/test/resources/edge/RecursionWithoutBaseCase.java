// expect: STEP_LIMIT
// message: down() calling itself again and again
public class Main {
    static int down(int n) {
        return down(n + 1) + 1;
    }

    public static void main(String[] args) {
        System.out.println(down(0));
    }
}
