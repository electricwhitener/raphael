// Level order (breadth-first): visit the root, then everything one level down, and so on.
// A queue holds the nodes waiting their turn: take one out, put its children in.
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

public class LevelOrder {
    static class Node {
        int val;
        Node left;
        Node right;

        Node(int val) {
            this.val = val;
        }

        Node(int val, Node left, Node right) {
            this.val = val;
            this.left = left;
            this.right = right;
        }
    }

    public static void main(String[] args) {
        //        1
        //      /   \
        //     2     3
        //    / \     \
        //   4   5     6
        Node root = new Node(1, new Node(2, new Node(4), new Node(5)), new Node(3, null, new Node(6)));
        List<Integer> order = new ArrayList<>();
        ArrayDeque<Node> queue = new ArrayDeque<>();
        queue.add(root);
        while (!queue.isEmpty()) {
            Node n = queue.poll();
            order.add(n.val);
            if (n.left != null) {
                queue.add(n.left);
            }
            if (n.right != null) {
                queue.add(n.right);
            }
        }
        System.out.println(order);
    }
}
