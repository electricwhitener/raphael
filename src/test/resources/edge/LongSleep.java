// expect: TIMEOUT
public class Main {
    public static void main(String[] args) throws InterruptedException {
        System.out.println("going to sleep");
        Thread.sleep(8000);
        System.out.println("woke up");
    }
}
