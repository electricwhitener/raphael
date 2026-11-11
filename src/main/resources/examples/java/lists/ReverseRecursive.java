// Reverse recursively: reverse everything after the head, then hook the head on the end.
// The deepest call finds the new head and every call passes it back up.
public class ReverseRecursive {
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

    static Node reverse(Node head) {
        if (head == null || head.next == null) {
            return head;               // the last node is the new head
        }
        Node newHead = reverse(head.next);
        head.next.next = head;         // the node after me points back at me
        head.next = null;
        return newHead;
    }

    public static void main(String[] args) {
        Node head = build(1, 2, 3, 4);
        head = reverse(head);
        System.out.println("new head: " + head.val);
    }
}
