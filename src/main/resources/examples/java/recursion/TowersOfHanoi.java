// Towers of Hanoi: to move n disks, move the top n-1 out of the way, move the biggest,
// then move the n-1 back on top of it. Each row of pegs is one peg, bottom disk first.
public class TowersOfHanoi {
    static int[][] pegs = new int[3][3];
    static int[] heights = {3, 0, 0};

    static void move(int n, int from, int to, int spare) {
        if (n == 0) {
            return;
        }
        move(n - 1, from, spare, to);
        int disk = pegs[from][--heights[from]];    // take the top disk
        pegs[from][heights[from]] = 0;
        pegs[to][heights[to]++] = disk;            // put it on the other peg
        move(n - 1, spare, to, from);
    }

    public static void main(String[] args) {
        pegs[0] = new int[] {3, 2, 1};             // 3 is the biggest disk
        move(3, 0, 2, 1);
        System.out.println(java.util.Arrays.toString(pegs[2]));
    }
}
