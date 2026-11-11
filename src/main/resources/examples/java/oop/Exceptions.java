// Exceptions: throw, catch, finally, an exception of your own, and one exception wrapped in
// another. Step through it and watch the red jump in the line numbers: from the line that
// throws to the catch that handles it.
public class Exceptions {

    // A checked exception of our own: whoever calls pop() must catch it or declare it.
    static class StackEmptyException extends Exception {
        StackEmptyException(String message) {
            super(message);
        }
    }

    static class Stack {
        int[] items = new int[4];
        int size = 0;

        void push(int value) {
            if (size == items.length) {
                throw new IllegalStateException("the stack is full");
            }
            items[size] = value;
            size++;
        }

        int pop() throws StackEmptyException {
            if (size == 0) {
                throw new StackEmptyException("pop on an empty stack");
            }
            size--;
            return items[size];
        }
    }

    // Turns one exception into another that says more, keeping the first as its cause.
    static int readAge(String text) {
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("not an age: " + text, e);
        }
    }

    public static void main(String[] args) {
        Stack stack = new Stack();
        stack.push(10);
        stack.push(20);
        try {
            while (true) {
                int top = stack.pop();
                System.out.println("popped " + top);
            }
        } catch (StackEmptyException e) {
            System.out.println("stopped: " + e.getMessage());
        } finally {
            System.out.println("finally runs either way");
        }

        String[] answers = {"19", "twenty"};
        for (String answer : answers) {
            try {
                int age = readAge(answer);
                System.out.println("age " + age);
            } catch (IllegalArgumentException e) {
                System.out.println(e.getMessage() + ", because " + e.getCause().getMessage());
            }
        }

        try {
            for (int i = 1; i <= 5; i++) {
                stack.push(i);
            }
        } catch (IllegalStateException e) {
            System.out.println("full after " + stack.size + " pushes");
        }
    }
}
