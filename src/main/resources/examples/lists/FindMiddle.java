// Find the middle in one pass: slow moves one node at a time, fast moves two. When fast
// reaches the end, slow is halfway.
public class FindMiddle {
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
        Node head = build(1, 2, 3, 4, 5);
        Node slow = head;
        Node fast = head;
        while (fast != null && fast.next != null) {
            slow = slow.next;
            fast = fast.next.next;
        }
        System.out.println("middle: " + slow.val);
    }
}
