// Preorder: the node first, then its left subtree, then its right. A copy of a tree is made
// in this order, because each parent exists before its children.
import java.util.ArrayList;
import java.util.List;

public class Preorder {
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

    static List<Integer> order = new ArrayList<>();

    static void visit(Node n) {
        if (n == null) {
            return;
        }
        order.add(n.val);              // before the two sides
        visit(n.left);
        visit(n.right);
    }

    public static void main(String[] args) {
        //        1
        //      /   \
        //     2     3
        //    / \     \
        //   4   5     6
        Node root = new Node(1, new Node(2, new Node(4), new Node(5)), new Node(3, null, new Node(6)));
        visit(root);
        System.out.println(order);
    }
}
