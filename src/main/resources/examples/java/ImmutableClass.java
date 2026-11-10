// An immutable class: private final fields and no setters. To "change" it you make a new
// object, as String does. Watch: the old Money object is untouched, and a new one appears.
public class ImmutableClass {
    static final class Money {
        private final int rupees;

        Money(int rupees) {
            this.rupees = rupees;
        }

        Money plus(int more) {
            return new Money(rupees + more);    // moment: a new object, because this one can never change
        }

        int getRupees() {
            return rupees;
        }
    }

    public static void main(String[] args) {
        Money price = new Money(100);
        Money withTax = price.plus(18);
        System.out.println(price.getRupees() + " then " + withTax.getRupees());
    }
}
