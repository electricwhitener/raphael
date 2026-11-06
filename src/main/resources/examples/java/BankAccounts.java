// OOP in action: an interface, an abstract class, inheritance, polymorphism and exceptions.
// Watch each box's title say which class it extends, and only the changed field flash.
interface Printable {
    default void print(String text) {
        System.out.println("> " + text);
    }
}

abstract class Account implements Printable {
    private static int nextId = 1;   // static: one counter shared by every account
    protected final int id;
    protected double balance;

    Account(double balance) {
        this.id = nextId++;
        this.balance = balance;
    }

    abstract double interest();      // abstract: each kind of account writes its own

    void deposit(double amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
        balance += amount;
    }
}

class Savings extends Account {
    Savings(double balance) {
        super(balance);              // runs Account's constructor first
    }

    @Override
    double interest() {
        return balance * 0.05;
    }
}

class Current extends Account {
    Current(double balance) {
        super(balance);
    }

    @Override
    double interest() {
        return 0;
    }
}

public class BankAccounts {
    public static void main(String[] args) {
        Account[] accounts = {new Savings(100), new Current(50)};
        for (Account a : accounts) {
            a.deposit(10);
            a.print(a.id + " earns " + a.interest());   // polymorphism: each runs its own interest()
        }
        try {
            accounts[0].deposit(-1);
        } catch (IllegalArgumentException e) {
            accounts[0].print(e.getMessage());
        }
    }
}
