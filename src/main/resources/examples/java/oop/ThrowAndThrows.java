// throw starts an exception; throws in a method's heading warns callers it may come out.
// A checked exception (like Exception) must be caught or declared; an unchecked one
// (a RuntimeException, like IllegalArgumentException) need not be.
public class ThrowAndThrows {
    static void checkAge(int age) {
        if (age < 0) {
            throw new IllegalArgumentException("age can't be negative");  // unchecked: no throws needed
        }
    }

    static void login(String password) throws Exception {
        if (password.length() < 6) {
            throw new Exception("password too short");                    // checked: login must say throws
        }
    }

    public static void main(String[] args) {
        try {
            checkAge(-4);
        } catch (IllegalArgumentException e) {
            System.out.println("caught: " + e.getMessage());
        }
        try {
            login("abc");
        } catch (Exception e) {
            System.out.println("caught: " + e.getMessage());
        }
    }
}
