// Insert at a position: stop at the node just before it, point the new node at the one after,
// then point the one before at the new node. In that order, or the rest of the list is lost.
public class InsertAtPosition {
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

    static Node insertAt(Node head, int index, int val) {
        Node node = new Node(val);
        if (index == 0) {
            node.next = head;
            return node;
        }
        Node prev = head;
        for (int i = 0; i < index - 1; i++) {
            prev = prev.next;
        }
        node.next = prev.next;         // first hold on to the rest
        prev.next = node;              // then link it in
        return head;
    }

    public static void main(String[] args) {
        Node head = build(10, 20, 40);
        head = insertAt(head, 2, 30);
        head = insertAt(head, 0, 5);
        System.out.println("head is " + head.val);
    }
}
