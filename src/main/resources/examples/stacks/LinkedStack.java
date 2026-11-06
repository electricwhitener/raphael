// A stack made of linked nodes: push adds a node at the head, pop removes the head.
// It never fills up, unlike an array.
public class LinkedStack {
    static class Node {
        int val;
        Node next;

        Node(int val, Node next) {
            this.val = val;
            this.next = next;
        }
    }

    static Node top = null;

    static void push(int value) {
        top = new Node(value, top);
    }

    static int pop() {
        int value = top.val;
        top = top.next;
        return value;
    }

    public static void main(String[] args) {
        push(1);
        push(2);
        push(3);
        System.out.println(pop() + " " + pop());
        push(4);
        System.out.println(pop() + " " + pop());
    }
}
