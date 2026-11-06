// Deleting from a binary search tree has three cases:
//   a leaf: just remove it;
//   one child: the child takes its place;
//   two children: copy in the smallest value of its right side, then delete that one instead.
public class BSTDelete {
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

    static Node delete(Node n, int val) {
        if (n == null) {
            return null;
        }
        if (val < n.val) {
            n.left = delete(n.left, val);
        } else if (val > n.val) {
            n.right = delete(n.right, val);
        } else if (n.left == null) {
            return n.right;            // no left child: the right one moves up
        } else if (n.right == null) {
            return n.left;
        } else {
            Node next = n.right;       // the smallest value bigger than n
            while (next.left != null) {
                next = next.left;
            }
            n.val = next.val;
            n.right = delete(n.right, next.val);
        }
        return n;
    }

    public static void main(String[] args) {
        Node root = null;
        for (int v : new int[] {50, 30, 70, 20, 40, 60, 80, 65}) {
            root = insert(root, v);
        }
        root = delete(root, 20);       // a leaf
        root = delete(root, 60);       // one child
        root = delete(root, 50);       // two children: the root itself
        System.out.println("new root: " + root.val);
    }
}
