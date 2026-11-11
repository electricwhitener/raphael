// Delete the tail: stop at the second-to-last node and set its next to null. A one-node list
// is a special case: deleting its tail leaves it empty.
public class DeleteTail {
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

    static Node deleteTail(Node head) {
        if (head == null || head.next == null) {
            return null;
        }
        Node curr = head;
        while (curr.next.next != null) {
            curr = curr.next;
        }
        curr.next = null;              // cut off the last node
        return head;
    }

    public static void main(String[] args) {
        Node head = build(1, 2, 3, 4);
        head = deleteTail(head);
        head = deleteTail(head);
        System.out.println("head is " + head.val);
    }
}
