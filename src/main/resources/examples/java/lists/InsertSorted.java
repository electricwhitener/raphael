// Insert in order: walk until the next node is bigger, and slot the new node in there.
// The list stays sorted however the values arrive.
public class InsertSorted {
    static class Node {
        int val;
        Node next;

        Node(int val) {
            this.val = val;
        }
    }

    static Node insertSorted(Node head, int val) {
        Node node = new Node(val);
        if (head == null || val < head.val) {
            node.next = head;          // it goes first
            return node;
        }
        Node curr = head;
        while (curr.next != null && curr.next.val < val) {
            curr = curr.next;
        }
        node.next = curr.next;
        curr.next = node;
        return head;
    }

    public static void main(String[] args) {
        Node head = null;
        for (int v : new int[] {5, 2, 8, 1, 6}) {
            head = insertSorted(head, v);
        }
        System.out.println("smallest: " + head.val);
    }
}
