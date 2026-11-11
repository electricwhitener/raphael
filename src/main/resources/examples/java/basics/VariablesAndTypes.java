// Variables and types: a variable's type decides what it can hold. Watch the Variables panel:
// an int keeps no fraction, a char is a number underneath, and a cast throws the fraction away.
public class VariablesAndTypes {
    public static void main(String[] args) {
        int count = 7;
        double price = 2.5;
        char letter = 'A';
        boolean ready = count > 5;
        String name = "Raphael";
        final int LIMIT = 10;                    // final: it can't be changed after this
        double total = count * price;
        int whole = (int) total;                 // moment: total is {total}; (int) keeps only the whole part
        int code = letter;                       // moment: a char is a number underneath: {letter} is stored as a number
        letter++;                                // moment: so letter + 1 is the next letter
        System.out.println(name + ": " + ready + " " + total + " " + whole + " " + code + " " + letter + " " + LIMIT);
    }
}
