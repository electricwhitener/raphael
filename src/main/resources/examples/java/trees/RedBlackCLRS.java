// Red-black tree insert, as in CLRS: insert like a BST and colour the new node red, then fix
// the colours going up. Case 1: the uncle is red, so recolour and move up. Case 2: the node is
// an inner child, so rotate it outward. Case 3: rotate the grandparent and recolour.
// Each node has a parent link (p); the drawing leaves it out, since the lines already show it.
public class RedBlackCLRS {
    static class Node {
        int key;
        char color = 'R';
        Node left, right, p;

        Node(int key) {
            this.key = key;
        }
    }

    static Node root;

    static char colorOf(Node n) {
        return n == null ? 'B' : n.color;   // missing children count as black
    }

    static void rotateLeft(Node x) {
        Node y = x.right;
        x.right = y.left;
        if (y.left != null) {
            y.left.p = x;
        }
        y.p = x.p;
        if (x.p == null) {
            root = y;
        } else if (x == x.p.left) {
            x.p.left = y;
        } else {
            x.p.right = y;
        }
        y.left = x;
        x.p = y;
    }

    static void rotateRight(Node x) {
        Node y = x.left;
        x.left = y.right;
        if (y.right != null) {
            y.right.p = x;
        }
        y.p = x.p;
        if (x.p == null) {
            root = y;
        } else if (x == x.p.right) {
            x.p.right = y;
        } else {
            x.p.left = y;
        }
        y.right = x;
        x.p = y;
    }

    static void insert(int key) {
        Node z = new Node(key);
        Node parent = null;
        Node x = root;
        while (x != null) {
            parent = x;
            x = key < x.key ? x.left : x.right;
        }
        z.p = parent;
        if (parent == null) {
            root = z;
        } else if (key < parent.key) {
            parent.left = z;
        } else {
            parent.right = z;
        }
        fixUp(z);
    }

    static void fixUp(Node z) {
        while (colorOf(z.p) == 'R') {
            Node g = z.p.p;
            boolean parentIsLeft = z.p == g.left;
            Node uncle = parentIsLeft ? g.right : g.left;
            if (colorOf(uncle) == 'R') {
                z.p.color = 'B';                  // moment: case 1 at {z.key}: the uncle is red, so recolour and move up
                uncle.color = 'B';
                g.color = 'R';
                z = g;
            } else {
                if (parentIsLeft ? z == z.p.right : z == z.p.left) {
                    z = z.p;                      // moment: case 2 at {z.key}: an inner child, so rotate it outward first
                    if (parentIsLeft) {
                        rotateLeft(z);
                    } else {
                        rotateRight(z);
                    }
                }
                z.p.color = 'B';                  // moment: case 3: rotate the grandparent and swap the colours
                z.p.p.color = 'R';
                if (parentIsLeft) {
                    rotateRight(z.p.p);
                } else {
                    rotateLeft(z.p.p);
                }
            }
        }
        root.color = 'B';
    }

    public static void main(String[] args) {
        for (int key : new int[] {41, 38, 31, 12, 19, 8}) {
            insert(key);
        }
        System.out.println("root " + root.key + " (" + root.color + ")");
    }
}
