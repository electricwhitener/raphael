// A stack keeps the last thing added on top: push puts a value on, pop takes the top one off.
// Here the stack is an array, and top counts how many values are in it.
public class ArrayStack {
    static int[] items = new int[5];
    static int top = 0;

    static void push(int value) {
        items[top] = value;
        top++;
    }

    static int pop() {
        top--;
        int value = items[top];
        items[top] = 0;                // not needed, but it makes the picture clearer
        return value;
    }

    public static void main(String[] args) {
        push(10);
        push(20);
        push(30);
        int a = pop();                 // 30: last in, first out
        push(40);
        int b = pop();
        int c = pop();
        System.out.println(a + " " + b + " " + c);
    }
}
