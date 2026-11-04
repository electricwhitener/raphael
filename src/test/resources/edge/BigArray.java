// expect: FINISHED
// output: 10000
public class Main {
    public static void main(String[] args) {
        int[] big = new int[10000];
        big[9999] = 7;
        long[][] grid = new long[300][300];
        grid[299][299] = 1;
        System.out.println(big.length);
    }
}
