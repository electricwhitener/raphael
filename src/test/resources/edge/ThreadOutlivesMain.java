// expect: TIMEOUT
public class Main {
    public static void main(String[] args) {
        Thread sleeper = new Thread(() -> {
            try {
                Thread.sleep(600000);
            } catch (InterruptedException e) {
                return;
            }
        });
        sleeper.start();
        System.out.println("main is done, but the sleeper keeps the program alive");
    }
}
