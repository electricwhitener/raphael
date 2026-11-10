// A B+ tree keeps every key in its leaves, and the leaves are linked left to right, so a range
// of keys is read by walking along the bottom. An inner node only holds copies of keys, to steer
// a search. Here: order 3 (at most 2 keys a node), keys 10 to 60, then the range 25..55.
public class BPlusTree {
    static final int MAX = 2;

    static class Node {
        int[] keys = new int[MAX + 1];
        int n;
        Node[] children = new Node[MAX + 2];
        boolean leaf = true;
        Node next;                 // the leaf to the right
    }

    static Node root = new Node();

    // Inserts into the subtree; returns a new right sibling if the node split, else null.
    static Node insert(Node node, int key, int[] up) {
        if (node.leaf) {
            int i = node.n;
            while (i > 0 && node.keys[i - 1] > key) {
                node.keys[i] = node.keys[i - 1];
                i--;
            }
            node.keys[i] = key;
            node.n++;
            if (node.n <= MAX) {
                return null;
            }
            Node right = new Node();                    // moment: the leaf is too full: split it, and copy its middle key up
            int half = node.n / 2;
            for (int k = half; k < node.n; k++) {
                right.keys[right.n++] = node.keys[k];
            }
            node.n = half;
            right.next = node.next;
            node.next = right;
            up[0] = right.keys[0];                      // copied up: it stays in the leaf too
            return right;
        }
        int c = 0;
        while (c < node.n && key >= node.keys[c]) {
            c++;
        }
        Node split = insert(node.children[c], key, up);
        if (split == null) {
            return null;
        }
        for (int k = node.n; k > c; k--) {
            node.keys[k] = node.keys[k - 1];
            node.children[k + 1] = node.children[k];
        }
        node.keys[c] = up[0];
        node.children[c + 1] = split;
        node.n++;
        if (node.n <= MAX) {
            return null;
        }
        Node right = new Node();                        // moment: an inner node is too full: its middle key moves up
        right.leaf = false;
        int mid = node.n / 2;
        up[0] = node.keys[mid];
        for (int k = mid + 1; k < node.n; k++) {
            right.keys[right.n] = node.keys[k];
            right.children[right.n] = node.children[k];
            right.n++;
        }
        right.children[right.n] = node.children[node.n];
        for (int k = mid + 1; k <= node.n; k++) {
            node.children[k] = null;
        }
        node.n = mid;
        return right;
    }

    static void add(int key) {
        int[] up = new int[1];
        Node split = insert(root, key, up);
        if (split != null) {                            // the root split: the tree grows a level
            Node top = new Node();
            top.leaf = false;
            top.keys[0] = up[0];
            top.children[0] = root;
            top.children[1] = split;
            top.n = 1;
            root = top;
        }
    }

    public static void main(String[] args) {
        for (int key : new int[] {10, 20, 30, 40, 50, 60}) {
            add(key);
        }
        Node leaf = root;
        while (!leaf.leaf) {
            leaf = leaf.children[0];                    // down to the leftmost leaf
        }
        StringBuilder range = new StringBuilder();
        for (; leaf != null; leaf = leaf.next) {        // moment: along the leaf links, left to right
            for (int k = 0; k < leaf.n; k++) {
                if (leaf.keys[k] >= 25 && leaf.keys[k] <= 55) {
                    range.append(leaf.keys[k]).append(' ');
                }
            }
        }
        System.out.println("25..55: " + range.toString().trim());
    }
}
