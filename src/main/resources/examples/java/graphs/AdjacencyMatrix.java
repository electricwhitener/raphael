// An adjacency matrix: with n vertices, an n x n table where row i, column j is 1 if there's
// an edge from i to j. Checking one edge is instant; listing neighbours means reading a row.
public class AdjacencyMatrix {
    static int[][] matrix = new int[4][4];

    static void addEdge(int a, int b) {
        matrix[a][b] = 1;
        matrix[b][a] = 1;              // no direction: mark both ways
    }

    public static void main(String[] args) {
        addEdge(0, 1);
        addEdge(0, 2);
        addEdge(1, 2);
        addEdge(2, 3);
        // how many neighbours (the degree) each vertex has
        int[] degree = new int[4];
        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 4; j++) {
                degree[i] += matrix[i][j];
            }
        }
        System.out.println(java.util.Arrays.toString(degree));
    }
}
