// Delete the head: the head moves to the second node. Nothing points at the old head any
// more, so Java will free it.
public class DeleteHead {
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

    static Node deleteHead(Node head) {
        if (head == null) {
            return null;               // nothing to delete
        }
        return head.next;
    }

    public static void main(String[] args) {
        Node head = build(1, 2, 3);
        head = deleteHead(head);
        head = deleteHead(head);
        System.out.println("head is " + head.val);
    }
}
