// Opening a file that isn't there throws FileNotFoundException, a checked exception: Java makes
// you catch it (or declare it). Here the catch makes the file, and the second try works.
import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.Scanner;

public class MissingFile {
    static int readNumber(String name) throws FileNotFoundException {
        try (Scanner in = new Scanner(new File(name))) {
            return in.nextInt();
        }
    }

    public static void main(String[] args) {
        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                int n = readNumber("settings.txt");
                System.out.println("attempt " + attempt + ": read " + n);
            } catch (FileNotFoundException e) {
                System.out.println("attempt " + attempt + ": no settings.txt, making one");
                try (PrintWriter out = new PrintWriter("settings.txt")) {
                    out.println(42);
                } catch (FileNotFoundException cannotWrite) {
                    System.out.println("couldn't make it either");
                }
            }
        }
    }
}
