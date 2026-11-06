public class BSTInsert {
    static class Node {
        int val;
        Node left, right;
        Node(int val) { this.val = val; }
    }

    static Node insert(Node n, int val) {
        if (n == null) {
            return new Node(val);
        }
        if (val < n.val) {
            n.left = insert(n.left, val);
        } else {
            n.right = insert(n.right, val);
        }
        return n;
    }

    public static void main(String[] args) {
        int[] values = {8, 3, 10, 1, 6, 14};
        Node root = null;
        for (int v : values) {
            root = insert(root, v);
        }
        System.out.println("Root: " + root.val);
    }
}
