// A CSV file has one record a line, with commas between the fields. Read each line, split it
// at the commas, and turn the parts into numbers.
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;

public class CsvParse {
    public static void main(String[] args) throws IOException {
        try (PrintWriter out = new PrintWriter("marks.csv")) {
            out.println("name,maths,physics");
            out.println("Asha,82,74");
            out.println("Ben,65,90");
        }
        try (BufferedReader in = new BufferedReader(new FileReader("marks.csv"))) {
            String header = in.readLine();       // the first line names the columns
            String line;
            while ((line = in.readLine()) != null) {
                String[] parts = line.split(",");  // moment: split(",") cuts {line} into its fields
                int sum = Integer.parseInt(parts[1]) + Integer.parseInt(parts[2]);
                System.out.println(parts[0] + ": " + sum);
            }
        }
    }
}
