// Counting words in a file: read each line, split it into words, and count each word in a
// HashMap. Watch the map grow as the lines are read.
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class WordCount {
    public static void main(String[] args) throws IOException {
        Files.write(Path.of("poem.txt"), List.of("the cat sat", "the cat ran", "a dog sat"));
        Map<String, Integer> counts = new TreeMap<>();   // a TreeMap keeps its words in order
        for (String line : Files.readAllLines(Path.of("poem.txt"))) {
            for (String word : line.split(" ")) {
                counts.put(word, counts.getOrDefault(word, 0) + 1);  // moment: one more {word}
            }
        }
        System.out.println(counts);
    }
}
