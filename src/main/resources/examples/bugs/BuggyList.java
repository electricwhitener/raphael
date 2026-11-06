public class BuggyList {
    static class Node {
        int val;
        Node next;
        Node(int val) { this.val = val; }
    }

    public static void main(String[] args) {
        Node head = new Node(1);
        head.next = new Node(2);
        head.next.next = new Node(3);
        head.next.next.next = new Node(4);

        // BUG: this reverse forgets to save curr.next before overwriting it.
        // Watch what happens to nodes 2, 3 and 4.
        Node prev = null;
        Node curr = head;
        while (curr != null) {
            curr.next = prev;
            prev = curr;
            curr = curr.next;   // this is now prev, not the old next!
        }
        head = prev;
        System.out.println("New head: " + head.val);
    }
}
