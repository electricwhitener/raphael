// Your own exception class. It extends Exception, so it's checked: the compiler makes every
// caller either catch it or declare it with throws.
class NotEnoughMoneyException extends Exception {
    final int missing;

    NotEnoughMoneyException(int missing) {
        super("short by " + missing);
        this.missing = missing;
    }
}

class Wallet {
    int money = 50;

    void pay(int amount) throws NotEnoughMoneyException {
        if (amount > money) {
            throw new NotEnoughMoneyException(amount - money);
        }
        money -= amount;
    }
}

public class CustomException {
    public static void main(String[] args) {
        Wallet wallet = new Wallet();
        try {
            wallet.pay(30);
            wallet.pay(40);            // only 20 left: this throws
            System.out.println("never printed");
        } catch (NotEnoughMoneyException e) {
            System.out.println("payment refused: " + e.getMessage());
        }
        System.out.println("left: " + wallet.money);
    }
}
