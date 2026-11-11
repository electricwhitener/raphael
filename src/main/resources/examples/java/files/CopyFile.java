// Copying a file line by line: read from one, write to the other. The copy fills up only
// when its writer is closed at the end of the try (the writer has a buffer).
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;

public class CopyFile {
    public static void main(String[] args) throws IOException {
        try (PrintWriter out = new PrintWriter("original.txt")) {
            out.println("first line");
            out.println("second line");
        }
        int copied = 0;
        try (BufferedReader in = new BufferedReader(new FileReader("original.txt"));
             PrintWriter out = new PrintWriter(new FileWriter("copy.txt"))) {
            String line;
            while ((line = in.readLine()) != null) {
                out.println(line);               // moment: {line} goes into the writer's buffer, not the file yet
                copied++;
            }
        }
        System.out.println("copied " + copied + " lines");
    }
}
