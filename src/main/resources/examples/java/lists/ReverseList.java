public class ReverseList {
    static class Node {
        int val;
        Node next;
        Node(int val) { this.val = val; }
    }

    public static void main(String[] args) {
        // Build 1 -> 2 -> 3 -> 4
        Node head = new Node(1);
        head.next = new Node(2);
        head.next.next = new Node(3);
        head.next.next.next = new Node(4);

        // Reverse it in place
        Node prev = null;
        Node curr = head;
        while (curr != null) {
            Node next = curr.next;
            curr.next = prev;
            prev = curr;
            curr = next;
        }
        head = prev;
        System.out.println("New head: " + head.val);
    }
}
