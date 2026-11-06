// Abstraction: Employee says every employee has a pay(), but not how it's worked out.
// Each subclass must write its own; an abstract class can't be made with new.
abstract class Employee {
    String name;

    Employee(String name) {
        this.name = name;
    }

    abstract double pay();         // no body here

    String payslip() {
        return name + ": " + pay();    // uses whichever pay() the object has
    }
}

class Salaried extends Employee {
    double monthly;

    Salaried(String name, double monthly) {
        super(name);
        this.monthly = monthly;
    }

    @Override
    double pay() {
        return monthly;
    }
}

class Hourly extends Employee {
    double rate;
    int hours;

    Hourly(String name, double rate, int hours) {
        super(name);
        this.rate = rate;
        this.hours = hours;
    }

    @Override
    double pay() {
        return rate * hours;
    }
}

public class Abstraction {
    public static void main(String[] args) {
        Employee[] staff = {new Salaried("Ana", 3000), new Hourly("Raj", 20, 60)};
        for (Employee e : staff) {
            System.out.println(e.payslip());
        }
    }
}
