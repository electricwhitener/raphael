// expect: FINISHED
// output: still fine
class Node {
    int val;
    Node left, right;
}

public class Main {
    static String name;
    static int[] empty;

    public static void main(String[] args) {
        Node root = null;
        Node[] slots = new Node[4];
        String[] words = new String[2];
        Node lonely = new Node();
        Object nothing = null;
        System.out.println("still fine");
    }
}
