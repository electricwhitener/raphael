// Flood fill (the paint bucket): colour a cell, then fill its four neighbours the same way.
// A call stops at the edge, at a wall (#) or at a cell that's already filled.
public class FloodFill {
    static void fill(char[][] grid, int r, int c) {
        if (r < 0 || c < 0 || r >= grid.length || c >= grid[0].length || grid[r][c] != '.') {
            return;
        }
        grid[r][c] = 'o';                        // moment: fill ({r}, {c}), then try its neighbours
        fill(grid, r + 1, c);
        fill(grid, r - 1, c);
        fill(grid, r, c + 1);
        fill(grid, r, c - 1);
    }

    public static void main(String[] args) {
        char[][] grid = {
            "..#".toCharArray(),
            ".##".toCharArray(),
            "#..".toCharArray(),
        };
        fill(grid, 0, 0);
        for (char[] row : grid) {
            System.out.println(new String(row));
        }
    }
}
