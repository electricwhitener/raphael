// Topological sort: an order for tasks where each comes after the tasks it needs (Kahn's way).
// indegree[v] counts what v still waits for; a task with 0 is ready. Doing it frees the tasks after it.
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

public class TopologicalSort {
    static String[] task = {"wake", "shower", "dress", "coffee", "leave"};
    static List<List<Integer>> after = new ArrayList<>();
    static int[] indegree = new int[task.length];

    static void before(int a, int b) {     // task a must come before task b
        after.get(a).add(b);
        indegree[b]++;
    }

    public static void main(String[] args) {
        for (int v = 0; v < task.length; v++) {
            after.add(new ArrayList<>());
        }
        before(0, 1);                  // wake before shower
        before(1, 2);                  // shower before dress
        before(0, 3);                  // wake before coffee
        before(2, 4);                  // dress before leave
        before(3, 4);                  // coffee before leave
        ArrayDeque<Integer> ready = new ArrayDeque<>();
        for (int v = 0; v < task.length; v++) {
            if (indegree[v] == 0) {
                ready.add(v);
            }
        }
        List<String> order = new ArrayList<>();
        while (!ready.isEmpty()) {
            int v = ready.poll();
            order.add(task[v]);
            for (int next : after.get(v)) {
                indegree[next]--;
                if (indegree[next] == 0) {
                    ready.add(next);   // everything it needed is done
                }
            }
        }
        System.out.println(order);
    }
}
