// expect: EXCEPTION
// message: OutOfMemoryError
public class Main {
    public static void main(String[] args) {
        long[][] hog = new long[100][];
        for (int i = 0; i < hog.length; i++) {
            hog[i] = new long[10_000_000];
        }
        System.out.println("never");
    }
}
