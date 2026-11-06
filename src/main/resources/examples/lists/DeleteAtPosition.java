// Delete at a position: count to the node before it, then unlink the one after.
public class DeleteAtPosition {
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

    static Node deleteAt(Node head, int index) {
        if (index == 0) {
            return head.next;
        }
        Node prev = head;
        for (int i = 0; i < index - 1; i++) {
            prev = prev.next;
        }
        prev.next = prev.next.next;
        return head;
    }

    public static void main(String[] args) {
        Node head = build(10, 20, 30, 40, 50);
        head = deleteAt(head, 2);      // removes 30
        head = deleteAt(head, 0);      // removes 10
        System.out.println("head is " + head.val);
    }
}
