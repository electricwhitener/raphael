// Searching a binary search tree: smaller values are always on the left, bigger on the right,
// so each comparison picks one side and ignores the other.
public class BSTSearch {
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

    static boolean contains(Node n, int val) {
        while (n != null) {
            if (val == n.val) {
                return true;
            }
            n = val < n.val ? n.left : n.right;
        }
        return false;
    }

    public static void main(String[] args) {
        Node root = null;
        for (int v : new int[] {50, 30, 70, 20, 40, 60, 80}) {
            root = insert(root, v);
        }
        System.out.println("has 60: " + contains(root, 60));
        System.out.println("has 35: " + contains(root, 35));
    }
}
