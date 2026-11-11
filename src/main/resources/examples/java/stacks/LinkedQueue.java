// A queue of linked nodes: add at the tail, remove at the head. Keeping a pointer to the tail
// means adding never has to walk the list.
public class LinkedQueue {
    static class Node {
        int val;
        Node next;

        Node(int val) {
            this.val = val;
        }
    }

    static Node head = null;
    static Node tail = null;

    static void enqueue(int value) {
        Node node = new Node(value);
        if (tail == null) {
            head = node;               // the queue was empty
        } else {
            tail.next = node;
        }
        tail = node;
    }

    static int dequeue() {
        int value = head.val;
        head = head.next;
        if (head == null) {
            tail = null;               // now it's empty
        }
        return value;
    }

    public static void main(String[] args) {
        enqueue(1);
        enqueue(2);
        enqueue(3);
        System.out.println(dequeue() + " " + dequeue());
        enqueue(4);
        System.out.println(dequeue() + " " + dequeue());
    }
}
