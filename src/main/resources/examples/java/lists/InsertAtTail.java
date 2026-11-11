// Insert at the tail: walk to the last node (the one whose next is null) and link the new
// node after it. An empty list is a special case: the new node is the head.
public class InsertAtTail {
    static class Node {
        int val;
        Node next;

        Node(int val) {
            this.val = val;
        }
    }

    static Node insertAtTail(Node head, int val) {
        Node node = new Node(val);
        if (head == null) {
            return node;
        }
        Node curr = head;
        while (curr.next != null) {
            curr = curr.next;
        }
        curr.next = node;
        return head;
    }

    public static void main(String[] args) {
        Node head = null;
        head = insertAtTail(head, 1);
        head = insertAtTail(head, 2);
        head = insertAtTail(head, 3);
        System.out.println("head is still " + head.val);
    }
}
