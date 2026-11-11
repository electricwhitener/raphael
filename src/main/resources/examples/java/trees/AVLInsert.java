// AVL tree insert: a BST that keeps every node's two subtrees within one level of each other.
// Each node stores its height (shown under it). After an insert, a node that's out of balance
// is fixed by a rotation: LL and RR need one, LR and RL need two. Watch the case named each time.
public class AVLInsert {
    static class Node {
        int key;
        int height = 1;
        Node left, right;

        Node(int key) {
            this.key = key;
        }
    }

    static int height(Node n) {
        return n == null ? 0 : n.height;
    }

    static void update(Node n) {
        n.height = 1 + Math.max(height(n.left), height(n.right));
    }

    static Node rotateRight(Node y) {
        Node x = y.left;
        y.left = x.right;
        x.right = y;
        update(y);
        update(x);
        return x;
    }

    static Node rotateLeft(Node x) {
        Node y = x.right;
        x.right = y.left;
        y.left = x;
        update(x);
        update(y);
        return y;
    }

    static Node insert(Node node, int key) {
        if (node == null) {
            return new Node(key);
        }
        if (key < node.key) {
            node.left = insert(node.left, key);
        } else {
            node.right = insert(node.right, key);
        }
        update(node);
        int balance = height(node.left) - height(node.right);
        if (balance > 1 && key < node.left.key) {
            return rotateRight(node);                        // moment: LL case at {node.key}: one right rotation
        }
        if (balance < -1 && key > node.right.key) {
            return rotateLeft(node);                         // moment: RR case at {node.key}: one left rotation
        }
        if (balance > 1) {
            node.left = rotateLeft(node.left);               // moment: LR case at {node.key}: rotate its left child left, then it right
            return rotateRight(node);
        }
        if (balance < -1) {
            node.right = rotateRight(node.right);            // moment: RL case at {node.key}: rotate its right child right, then it left
            return rotateLeft(node);
        }
        return node;
    }

    public static void main(String[] args) {
        Node root = null;
        for (int key : new int[] {10, 20, 30, 50, 40, 5, 1, 7, 8}) {  // RR, RL, LL, then LR
            root = insert(root, key);
        }
        System.out.println("root " + root.key + ", height " + root.height);
    }
}
