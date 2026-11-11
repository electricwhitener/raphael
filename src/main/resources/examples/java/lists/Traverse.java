// Walking a list: start at the head and follow next until it is null.
public class Traverse {
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
        Node head = build(4, 8, 15, 16);
        int count = 0;
        int sum = 0;
        Node curr = head;
        while (curr != null) {
            count++;
            sum += curr.val;
            curr = curr.next;          // one step along
        }
        System.out.println(count + " nodes, sum " + sum);
    }
}
