// BUG: this finds the last node of the list, but it crashes. Step to the end and look at curr.
public class NullNode {
    static class Node {
        int val;
        Node next;

        Node(int val) {
            this.val = val;
        }
    }

    public static void main(String[] args) {
        Node head = new Node(1);
        head.next = new Node(2);
        head.next.next = new Node(3);
        Node curr = head;
        while (curr != null) {         // should stop one node sooner: curr.next != null
            curr = curr.next;
        }
        System.out.println("last: " + curr.val);
    }
}
