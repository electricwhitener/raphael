// In a sorted list, equal values sit side by side. Skip each node that equals the one before it.
public class RemoveDuplicates {
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

    public static void main(String[] args) {
        Node head = build(1, 1, 2, 3, 3, 3, 4);
        Node curr = head;
        while (curr != null && curr.next != null) {
            if (curr.next.val == curr.val) {
                curr.next = curr.next.next;    // drop the copy, stay here
            } else {
                curr = curr.next;
            }
        }
        System.out.println("head is " + head.val);
    }
}
