// Insert at the head: the new node points at the old head, then becomes the head. It takes
// the same few steps however long the list is.
public class InsertAtHead {
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

    static Node insertAtHead(Node head, int val) {
        Node node = new Node(val);
        node.next = head;
        return node;                   // the new head
    }

    public static void main(String[] args) {
        Node head = build(2, 3);
        head = insertAtHead(head, 1);
        head = insertAtHead(head, 0);
        System.out.println("head is " + head.val);
    }
}
