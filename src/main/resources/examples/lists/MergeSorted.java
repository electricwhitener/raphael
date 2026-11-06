// Merge two sorted lists into one: compare the two front nodes and link the smaller one on.
// A dummy node at the start means the first link needs no special case.
public class MergeSorted {
    static class Node {
        int val;
        Node next;

        Node(int val) {
            this.val = val;
        }
    }

    // Builds a list from the values, front to back.
    static Node build(int... values) {
        Node head = null;
        Node tail = null;
        for (int v : values) {
            Node node = new Node(v);
            if (head == null) {
                head = node;
            } else {
                tail.next = node;
            }
            tail = node;
        }
        return head;
    }

    static Node merge(Node a, Node b) {
        Node dummy = new Node(0);
        Node tail = dummy;
        while (a != null && b != null) {
            if (a.val <= b.val) {
                tail.next = a;
                a = a.next;
            } else {
                tail.next = b;
                b = b.next;
            }
            tail = tail.next;
        }
        tail.next = (a != null) ? a : b;   // the rest of whichever is left
        return dummy.next;
    }

    public static void main(String[] args) {
        Node a = build(1, 4, 7);
        Node b = build(2, 3, 8);
        Node merged = merge(a, b);
        System.out.println("starts with " + merged.val);
    }
}
