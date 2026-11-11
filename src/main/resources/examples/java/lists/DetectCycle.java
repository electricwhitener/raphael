// Does the list loop back on itself? Floyd's idea: a slow pointer and a fast one. In a loop,
// fast laps slow and they meet; without one, fast just reaches null.
public class DetectCycle {
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
        head.next.next.next.next.next = head.next.next;    // 5 points back at 3
        Node slow = head;
        Node fast = head;
        boolean loop = false;
        while (fast != null && fast.next != null) {
            slow = slow.next;
            fast = fast.next.next;
            if (slow == fast) {
                loop = true;
                break;
            }
        }
        System.out.println(loop ? "there is a cycle, met at " + slow.val : "no cycle");
    }
}
