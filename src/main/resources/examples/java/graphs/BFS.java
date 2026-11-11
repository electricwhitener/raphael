// Breadth-first search: visit the start, then all its neighbours, then theirs. A queue keeps
// the order: vertices found first are visited first. The adjacency list's row v holds v's neighbours.
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

public class BFS {
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
        boolean[] visited = new boolean[n];
        List<Integer> order = new ArrayList<>();
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        queue.add(0);
        visited[0] = true;
        while (!queue.isEmpty()) {
            int v = queue.poll();
            order.add(v);
            for (int next : adj.get(v)) {
                if (!visited[next]) {
                    visited[next] = true;  // mark it now, so it's queued only once
                    queue.add(next);
                }
            }
        }
        System.out.println(order);
    }
}
