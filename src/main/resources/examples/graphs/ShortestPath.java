// The fewest edges from 0 to every vertex. BFS reaches vertices in order of distance, so the
// first time a vertex is found is the shortest way to it: one more than the vertex it came from.
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ShortestPath {
    static void addEdge(List<List<Integer>> adj, int a, int b) {
        adj.get(a).add(b);             // both ways: the edges have no direction
        adj.get(b).add(a);
    }

    public static void main(String[] args) {
        // 0 - 1 - 3
        // |   |   |
        // 2 - 4   5
        int n = 6;
        List<List<Integer>> adj = new ArrayList<>();
        for (int v = 0; v < n; v++) {
            adj.add(new ArrayList<>());
        }
        addEdge(adj, 0, 1);
        addEdge(adj, 0, 2);
        addEdge(adj, 1, 3);
        addEdge(adj, 1, 4);
        addEdge(adj, 2, 4);
        addEdge(adj, 3, 5);
        int[] dist = new int[n];
        Arrays.fill(dist, -1);         // -1: not reached yet
        dist[0] = 0;
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        queue.add(0);
        while (!queue.isEmpty()) {
            int v = queue.poll();
            for (int next : adj.get(v)) {
                if (dist[next] == -1) {
                    dist[next] = dist[v] + 1;
                    queue.add(next);
                }
            }
        }
        System.out.println(Arrays.toString(dist));
    }
}
