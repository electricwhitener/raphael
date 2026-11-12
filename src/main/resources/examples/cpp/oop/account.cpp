#include <iostream>
using namespace std;

// balance is private: only Account's own methods can change it, and deposit checks the amount.
class Account {
    double balance;

public:
    Account(double start) : balance(start) {}

    void deposit(double amount) {
        if (amount > 0) {
            balance += amount;
        }
    }

    double getBalance() const {
        return balance;
    }
};

int main() {
    Account a(100);
    a.deposit(50);
    a.deposit(-20); // refused: the balance stays 150
    cout << a.getBalance() << endl;
    return 0;
}
