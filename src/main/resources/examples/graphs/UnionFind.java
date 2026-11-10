// Union-find (disjoint sets): parent[i] is i's parent, and a root is its own parent, so each
// set is a tree. find() walks up to the root and points every node it passed straight at the
// root (path compression); union() hangs the shorter tree under the taller one (union by rank).
public class UnionFind {
    static int[] parent = new int[7];
    static int[] rank = new int[7];

    static int find(int x) {
        if (parent[x] != x) {
            parent[x] = find(parent[x]);         // moment: path compression: {x} will point straight at its root
        }
        return parent[x];
    }

    static void union(int a, int b) {
        int ra = find(a);
        int rb = find(b);
        if (ra == rb) {
            return;                              // already in the same set
        }
        if (rank[ra] < rank[rb]) {
            parent[ra] = rb;
        } else if (rank[ra] > rank[rb]) {
            parent[rb] = ra;
        } else {
            parent[rb] = ra;                     // moment: two trees of the same rank: {rb} goes under {ra}, which grows
            rank[ra]++;
        }
    }

    public static void main(String[] args) {
        for (int i = 0; i < parent.length; i++) {
            parent[i] = i;                       // every element starts in a set of its own
        }
        union(0, 1);
        union(2, 3);
        union(1, 3);
        union(4, 5);
        union(5, 6);
        union(6, 3);
        System.out.println(find(3) == find(5) ? "3 and 5 are connected" : "not connected");
    }
}
