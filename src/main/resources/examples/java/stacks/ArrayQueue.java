// A queue: first in, first out. In an array, front and back move right as values come and go,
// and wrap round to 0 at the end (% length), so the freed cells are used again.
public class ArrayQueue {
    static int[] items = new int[4];
    static int front = 0;              // the next value to leave
    static int back = 0;               // where the next value goes
    static int size = 0;

    static void enqueue(int value) {
        items[back] = value;
        back = (back + 1) % items.length;
        size++;
    }

    static int dequeue() {
        int value = items[front];
        items[front] = 0;
        front = (front + 1) % items.length;
        size--;
        return value;
    }

    public static void main(String[] args) {
        enqueue(1);
        enqueue(2);
        enqueue(3);
        int a = dequeue();             // 1: first in, first out
        int b = dequeue();
        enqueue(4);
        enqueue(5);                    // back wraps round to index 0
        System.out.println(a + " " + b + " " + dequeue() + " " + dequeue());
    }
}
