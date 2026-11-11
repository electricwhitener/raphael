// A trie stores words letter by letter. Words that start the same share their first nodes,
// so finding every word with a prefix is quick. children[0] is 'a', children[1] is 'b', …
public class Trie {
    static class Node {
        Node[] children = new Node[26];
        boolean isWord;                // a word ends here
    }

    static Node root = new Node();

    static void insert(String word) {
        Node n = root;
        for (char c : word.toCharArray()) {
            int i = c - 'a';
            if (n.children[i] == null) {
                n.children[i] = new Node();
            }
            n = n.children[i];
        }
        n.isWord = true;
    }

    static boolean contains(String word) {
        Node n = root;
        for (char c : word.toCharArray()) {
            n = n.children[c - 'a'];
            if (n == null) {
                return false;
            }
        }
        return n.isWord;
    }

    public static void main(String[] args) {
        insert("car");
        insert("cat");
        insert("cup");
        System.out.println(contains("cat") + " " + contains("ca"));
    }
}
