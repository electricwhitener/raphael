// Red-black tree, left-leaning (Sedgewick): a red link joins a node to its parent as if they
// were one 3-node. New nodes are red. Three fixes keep it balanced on the way back up:
// a red right link rotates left, two reds in a row rotate right, and two red children flip colours.
public class RedBlackLLRB {
    static final boolean RED = true;
    static final boolean BLACK = false;

    static class Node {
        int key;
        boolean color = RED;
        Node left, right;

        Node(int key) {
            this.key = key;
        }
    }

    static boolean isRed(Node n) {
        return n != null && n.color == RED;
    }

    static Node rotateLeft(Node h) {
        Node x = h.right;
        h.right = x.left;
        x.left = h;
        x.color = h.color;
        h.color = RED;
        return x;
    }

    static Node rotateRight(Node h) {
        Node x = h.left;
        h.left = x.right;
        x.right = h;
        x.color = h.color;
        h.color = RED;
        return x;
    }

    static void flipColors(Node h) {
        h.color = RED;
        h.left.color = BLACK;
        h.right.color = BLACK;
    }

    static Node insert(Node h, int key) {
        if (h == null) {
            return new Node(key);
        }
        if (key < h.key) {
            h.left = insert(h.left, key);
        } else {
            h.right = insert(h.right, key);
        }
        if (isRed(h.right) && !isRed(h.left)) {
            h = rotateLeft(h);                    // moment: a red right link at {h.key}: rotate left
        }
        if (isRed(h.left) && isRed(h.left.left)) {
            h = rotateRight(h);                   // moment: two reds in a row under {h.key}: rotate right
        }
        if (isRed(h.left) && isRed(h.right)) {
            flipColors(h);                        // moment: both children of {h.key} are red: flip the colours
        }
        return h;
    }

    public static void main(String[] args) {
        Node root = null;
        for (int key : new int[] {10, 20, 30, 15, 25}) {
            root = insert(root, key);
            root.color = BLACK;                   // the root is always black
        }
        System.out.println("root " + root.key);
    }
}
