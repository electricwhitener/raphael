public class OffByOne {
    public static void main(String[] args) {
        int[] scores = {4, 7, 1};
        int sum = 0;
        // BUG: <= should be <. Watch the marker for i walk past the end.
        for (int i = 0; i <= scores.length; i++) {
            sum += scores[i];
        }
        System.out.println("Total: " + sum);
    }
}
