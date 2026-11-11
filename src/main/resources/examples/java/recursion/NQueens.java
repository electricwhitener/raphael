// Place 4 queens on a 4x4 board so none can attack another. Put a queen in each row, in the
// first safe column; if a row has no safe column, go back and move the queen above (backtrack).
public class NQueens {
    static int n = 4;
    static int[][] board = new int[n][n];

    static boolean safe(int row, int col) {
        for (int r = 0; r < row; r++) {
            int c = col - (row - r);   // the two diagonals and the column above
            int d = col + (row - r);
            if (board[r][col] == 1 || (c >= 0 && board[r][c] == 1) || (d < n && board[r][d] == 1)) {
                return false;
            }
        }
        return true;
    }

    static boolean place(int row) {
        if (row == n) {
            return true;               // every row has a queen
        }
        for (int col = 0; col < n; col++) {
            if (safe(row, col)) {
                board[row][col] = 1;
                if (place(row + 1)) {
                    return true;
                }
                board[row][col] = 0;   // a dead end: take it back
            }
        }
        return false;
    }

    public static void main(String[] args) {
        place(0);
        for (int[] row : board) {
            System.out.println(java.util.Arrays.toString(row));
        }
    }
}
