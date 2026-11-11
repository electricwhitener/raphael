// Dijkstra's shortest paths, when edges have weights (lengths). Each round, take the closest
// vertex not done yet; its distance is final. Then see if going through it is shorter to its neighbours.
// w[i][j] is the length of the edge from i to j, or 0 for no edge.
import java.util.Arrays;

public class Dijkstra {
    public static void main(String[] args) {
        int[][] w = {
            {0, 4, 1, 0, 0},
            {4, 0, 2, 5, 0},
            {1, 2, 0, 8, 0},
            {0, 5, 8, 0, 3},
            {0, 0, 0, 3, 0}
        };
        int n = w.length;
        int[] dist = new int[n];
        boolean[] done = new boolean[n];
        Arrays.fill(dist, 999);        // 999: no path found yet
        dist[0] = 0;
        for (int round = 0; round < n; round++) {
            int u = -1;
            for (int v = 0; v < n; v++) {
                if (!done[v] && (u == -1 || dist[v] < dist[u])) {
                    u = v;             // the closest vertex not done
                }
            }
            done[u] = true;
            for (int v = 0; v < n; v++) {
                if (w[u][v] > 0 && dist[u] + w[u][v] < dist[v]) {
                    dist[v] = dist[u] + w[u][v];   // a shorter way, through u
                }
            }
        }
        System.out.println(Arrays.toString(dist));
    }
}
