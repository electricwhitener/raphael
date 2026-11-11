// A Scanner can read a file the way it reads the keyboard. This writes numbers.txt, then reads
// it back number by number. Open the Files view: each number fades once it has been read.
import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.Scanner;

public class ReadNumbers {
    public static void main(String[] args) throws FileNotFoundException {
        try (PrintWriter out = new PrintWriter("numbers.txt")) {
            out.println(12);
            out.println(30);
            out.println(7);
        }
        int total = 0;
        try (Scanner in = new Scanner(new File("numbers.txt"))) {
            while (in.hasNextInt()) {
                int n = in.nextInt();
                total += n;                      // moment: {n} was read from the file
            }
        }
        System.out.println("total " + total);
    }
}
