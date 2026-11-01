public class GridPaths {
    // How many ways can you walk from the top-left to the bottom-right,
    // moving only right or down?
    public static void main(String[] args) {
        int rows = 4, cols = 5;
        int[][] paths = new int[rows][cols];
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                if (r == 0 || c == 0) {
                    paths[r][c] = 1;
                } else {
                    paths[r][c] = paths[r - 1][c] + paths[r][c - 1];
                }
            }
        }
        System.out.println("Paths: " + paths[rows - 1][cols - 1]);
    }
}
