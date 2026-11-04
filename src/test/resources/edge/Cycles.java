// expect: FINISHED
// output: done
class Node {
    int val;
    Node next;

    Node(int val) {
        this.val = val;
    }
}

class Pair {
    Pair other;
}

public class Main {
    public static void main(String[] args) {
        Node self = new Node(1);
        self.next = self;
        Node a = new Node(2);
        Node b = new Node(3);
        a.next = b;
        b.next = a;
        Pair p = new Pair();
        Pair q = new Pair();
        p.other = q;
        q.other = p;
        System.out.println("done");
    }
}
