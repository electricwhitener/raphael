// Adding to the end of a file: new FileWriter(name, true) appends instead of starting over.
// flush() pushes each line out of the writer's memory into the file at once, so the Files panel
// shows log.txt growing line by line. Then java.nio's Files.readAllLines reads it all in one call.
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class AppendLog {
    public static void main(String[] args) throws IOException {
        for (int day = 1; day <= 3; day++) {
            try (FileWriter log = new FileWriter("log.txt", true)) {   // true: append
                log.write("day " + day + ": ran " + (day * 2) + " km\n");
                log.flush();
            }
        }
        List<String> lines = Files.readAllLines(Path.of("log.txt"));
        System.out.println(lines.size() + " lines, last: " + lines.get(lines.size() - 1));
    }
}
