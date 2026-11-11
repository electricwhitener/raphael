// Transpose: row r of the matrix becomes column r of the result.
public class MatrixTranspose {
    public static void main(String[] args) {
        int[][] m = {
            {1, 2, 3},
            {4, 5, 6}
        };
        int[][] t = new int[3][2];
        for (int r = 0; r < 2; r++) {
            for (int c = 0; c < 3; c++) {
                t[c][r] = m[r][c];
            }
        }
        System.out.println(java.util.Arrays.deepToString(t));
    }
}
