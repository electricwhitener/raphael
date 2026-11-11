// An array is a row of numbered boxes, starting at 0; a 2D array is an array of rows.
// Watch i walk along the row while max keeps the biggest value seen so far.
public class ArraysBasics {
    public static void main(String[] args) {
        int[] marks = {62, 88, 45, 91, 70};
        int max = marks[0];
        for (int i = 1; i < marks.length; i++) {
            if (marks[i] > max) {
                max = marks[i];                  // moment: {marks[i]} is bigger than {max}, so it's the new max
            }
        }
        int[][] grid = new int[3][4];            // 3 rows of 4
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 4; c++) {
                grid[r][c] = r * 4 + c;
            }
        }
        System.out.println("max " + max + ", grid[2][3] = " + grid[2][3]);
    }
}
