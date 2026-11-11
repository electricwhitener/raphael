// Depth-first search without recursion: an explicit stack does the job of the call stack.
// Take the top vertex; if it's new, visit it and push its neighbours.
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

public class DFSStack {
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
        ArrayDeque<Integer> stack = new ArrayDeque<>();
        stack.push(0);
        while (!stack.isEmpty()) {
            int v = stack.pop();
            if (visited[v]) {
                continue;              // reached again by another path
            }
            visited[v] = true;
            order.add(v);
            for (int next : adj.get(v)) {
                if (!visited[next]) {
                    stack.push(next);
                }
            }
        }
        System.out.println(order);
    }
}
