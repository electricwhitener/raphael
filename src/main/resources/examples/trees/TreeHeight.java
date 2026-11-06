// Height and size, both recursive: ask each subtree for its answer, then combine them.
// An empty tree has height 0 and size 0.
public class TreeHeight {
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

    static int height(Node n) {
        if (n == null) {
            return 0;
        }
        return 1 + Math.max(height(n.left), height(n.right));
    }

    static int size(Node n) {
        if (n == null) {
            return 0;
        }
        return 1 + size(n.left) + size(n.right);
    }

    public static void main(String[] args) {
        //        1
        //      /   \
        //     2     3
        //    / \     \
        //   4   5     6
        Node root = new Node(1, new Node(2, new Node(4), new Node(5)), new Node(3, null, new Node(6)));
        System.out.println("height " + height(root) + ", " + size(root) + " nodes");
    }
}
