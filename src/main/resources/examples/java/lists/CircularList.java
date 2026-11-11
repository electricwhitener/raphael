// A circular list: the last node points back at the first, so there's no null at the end.
// A walk stops when it gets back to where it started.
public class CircularList {
    static class Node {
        int val;
        Node next;

        Node(int val) {
            this.val = val;
        }
    }

    public static void main(String[] args) {
        Node first = new Node(1);
        first.next = new Node(2);
        first.next.next = new Node(3);
        first.next.next.next = first;  // close the circle

        // walk once around, adding up the values
        int sum = 0;
        Node curr = first;
        do {
            sum += curr.val;
            curr = curr.next;
        } while (curr != first);
        System.out.println("sum once around: " + sum);
    }
}
