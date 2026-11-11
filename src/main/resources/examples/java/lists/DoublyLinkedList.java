// A doubly linked list: each node points at the next one and the previous one, so a node can
// be removed knowing only the node itself, and the list can be walked backwards.
public class DoublyLinkedList {
    static class Node {
        int val;
        Node prev;
        Node next;

        Node(int val) {
            this.val = val;
        }
    }

    // Puts a new node after the given one: four links change.
    static void insertAfter(Node node, int val) {
        Node added = new Node(val);
        added.prev = node;
        added.next = node.next;
        if (node.next != null) {
            node.next.prev = added;
        }
        node.next = added;
    }

    // Takes a node out by joining its neighbours to each other.
    static void remove(Node node) {
        if (node.prev != null) {
            node.prev.next = node.next;
        }
        if (node.next != null) {
            node.next.prev = node.prev;
        }
    }

    public static void main(String[] args) {
        Node head = new Node(1);
        insertAfter(head, 3);
        insertAfter(head, 2);          // goes between 1 and 3
        insertAfter(head.next.next, 4);
        remove(head.next.next);        // removes 3
        Node tail = head;
        while (tail.next != null) {
            tail = tail.next;
        }
        System.out.println("backwards from " + tail.val + " to " + tail.prev.prev.val);
    }
}
