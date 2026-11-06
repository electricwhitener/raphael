// Write a file, then read it back. try (…) closes each file when its block ends, even after an
// error. Watch the Files panel: a PrintWriter keeps its lines in memory, and scores.txt only
// fills up when the writer is closed at the end of the try.
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;

public class WriteAndRead {
    public static void main(String[] args) {
        String[] names = {"Asha", "Ben", "Chen"};
        int[] scores = {82, 67, 91};
        try (PrintWriter out = new PrintWriter(new FileWriter("scores.txt"))) {
            for (int i = 0; i < names.length; i++) {
                out.println(names[i] + "," + scores[i]);
            }
        } catch (IOException e) {
            System.out.println("couldn't write: " + e.getMessage());
        }

        // Read it back, one line at a time, and find the best score.
        String best = "";
        int bestScore = -1;
        try (BufferedReader in = new BufferedReader(new FileReader("scores.txt"))) {
            String line;
            while ((line = in.readLine()) != null) {
                String[] parts = line.split(",");
                int score = Integer.parseInt(parts[1]);
                if (score > bestScore) {
                    bestScore = score;
                    best = parts[0];
                }
            }
        } catch (IOException e) {
            System.out.println("couldn't read: " + e.getMessage());
        }
        System.out.println("best: " + best + " with " + bestScore);
    }
}
