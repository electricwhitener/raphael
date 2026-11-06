// Delete by value: find the node before the one to remove, and point it past it.
// If the head itself holds the value, the head moves on instead.
public class DeleteByValue {
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

    static Node delete(Node head, int val) {
        if (head == null) {
            return null;
        }
        if (head.val == val) {
            return head.next;
        }
        Node prev = head;
        while (prev.next != null && prev.next.val != val) {
            prev = prev.next;
        }
        if (prev.next != null) {
            prev.next = prev.next.next;    // skip over it
        }
        return head;
    }

    public static void main(String[] args) {
        Node head = build(3, 7, 9, 12);
        head = delete(head, 9);
        head = delete(head, 3);
        head = delete(head, 42);       // not there: nothing changes
        System.out.println("head is " + head.val);
    }
}
