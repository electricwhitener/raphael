// Depth-first search: from each vertex, go to its first unvisited neighbour, and keep going as
// deep as possible. At a dead end, the recursion returns and tries the next neighbour back there.
import java.util.ArrayList;
import java.util.List;

public class DFS {
    static List<List<Integer>> adj;
    static boolean[] visited;
    static List<Integer> order = new ArrayList<>();

    static void dfs(int v) {
        visited[v] = true;
        order.add(v);
        for (int next : adj.get(v)) {
            if (!visited[next]) {
                dfs(next);
            }
        }
    }

    static void addEdge(int a, int b) {
        adj.get(a).add(b);             // both ways: the edges have no direction
        adj.get(b).add(a);
    }

    public static void main(String[] args) {
        // 0 - 1 - 3
        // |   |   |
        // 2 - 4   5
        int n = 6;
        adj = new ArrayList<>();
        for (int v = 0; v < n; v++) {
            adj.add(new ArrayList<>());
        }
        addEdge(0, 1);
        addEdge(0, 2);
        addEdge(1, 3);
        addEdge(1, 4);
        addEdge(2, 4);
        addEdge(3, 5);
        visited = new boolean[n];
        dfs(0);
        System.out.println(order);
    }
}
