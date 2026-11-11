// try (…) closes what it opened when the block ends, even after an exception, newest first.
// Any class that implements AutoCloseable can be used this way. Watch close() run.
public class TryWithResources {
    static class Door implements AutoCloseable {
        private final String name;

        Door(String name) {
            this.name = name;
            System.out.println("open " + name);
        }

        @Override
        public void close() {
            System.out.println("close " + name); // moment: the try is over, so {name} is closed for you
        }
    }

    public static void main(String[] args) {
        try (Door front = new Door("front"); Door back = new Door("back")) {
            System.out.println("inside");
            throw new IllegalStateException("oops");
        } catch (IllegalStateException e) {
            System.out.println("caught " + e.getMessage() + " after both doors closed");
        }
    }
}
