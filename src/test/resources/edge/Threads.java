// expect: FINISHED
// output: total 200
public class Main {
    static int total = 0;

    static synchronized void add() {
        total++;
    }

    public static void main(String[] args) throws InterruptedException {
        Thread a = new Thread(() -> { for (int i = 0; i < 100; i++) add(); });
        Thread b = new Thread(() -> { for (int i = 0; i < 100; i++) add(); });
        a.start();
        b.start();
        a.join();
        b.join();
        System.out.println("total " + total);
    }
}
