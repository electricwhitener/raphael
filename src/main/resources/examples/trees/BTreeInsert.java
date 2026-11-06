public class BTreeInsert {
    static final int T = 2; // minimum degree: every node holds 1 to 3 keys (a 2-3-4 tree)

    static class Node {
        int[] keys = new int[2 * T - 1];
        Node[] children = new Node[2 * T];
        int n;               // how many keys are in use
        boolean leaf = true;
    }

    static Node root = new Node();

    static void insert(int key) {
        Node r = root;
        if (r.n == 2 * T - 1) {
            // The root is full: make a new root above it and split the old one.
            Node s = new Node();
            s.leaf = false;
            s.children[0] = r;
            root = s;
            splitChild(s, 0);
            insertNonFull(s, key);
        } else {
            insertNonFull(r, key);
        }
    }

    // x.children[i] is full: move its middle key up into x, and its right half into a new node z.
    static void splitChild(Node x, int i) {
        Node y = x.children[i];
        Node z = new Node();
        z.leaf = y.leaf;
        z.n = T - 1;
        for (int j = 0; j < T - 1; j++) {
            z.keys[j] = y.keys[j + T];
            y.keys[j + T] = 0;            // clear the moved slot, so the picture shows only real keys
        }
        if (!y.leaf) {
            for (int j = 0; j < T; j++) {
                z.children[j] = y.children[j + T];
                y.children[j + T] = null;
            }
        }
        // Make room in x for the new child and the middle key.
        for (int j = x.n; j > i; j--) {
            x.children[j + 1] = x.children[j];
        }
        x.children[i + 1] = z;
        for (int j = x.n - 1; j >= i; j--) {
            x.keys[j + 1] = x.keys[j];
        }
        x.keys[i] = y.keys[T - 1];
        y.keys[T - 1] = 0;
        y.n = T - 1;
        x.n++;
    }

    static void insertNonFull(Node x, int key) {
        int i = x.n - 1;
        if (x.leaf) {
            // Shift bigger keys one place right, then drop the new key into the gap.
            while (i >= 0 && key < x.keys[i]) {
                x.keys[i + 1] = x.keys[i];
                i--;
            }
            x.keys[i + 1] = key;
            x.n++;
        } else {
            // Find the child to go down into; split it first if it's full.
            while (i >= 0 && key < x.keys[i]) {
                i--;
            }
            i++;
            if (x.children[i].n == 2 * T - 1) {
                splitChild(x, i);
                if (key > x.keys[i]) {
                    i++;
                }
            }
            insertNonFull(x.children[i], key);
        }
    }

    public static void main(String[] args) {
        int[] values = {10, 20, 5, 6, 12, 30, 7, 17, 3, 25};
        for (int v : values) {
            insert(v);
        }
        System.out.println("Root key: " + root.keys[0]);
    }
}
