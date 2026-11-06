package raphael.trace;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

// The folder a program runs in: a new, empty one for each run, with the user's input files
// copied in. A program that writes "scores.txt" writes it here, not wherever Raphael was started.
// At each step Raphael reads the files in it, so the Files panel can show what the program
// wrote, step by step. The folder is deleted when the run ends.
// OOP: encapsulation (the folder's path and the last contents stay private); it implements
// AutoCloseable, so a try-with-resources block deletes the folder however the run ends.
public class RunFolder implements AutoCloseable {

    static final int MAX_FILES = 20;           // more than a small program writes
    static final int MAX_SHOWN_BYTES = 20_000; // a bigger file is listed, but not read

    private final Path path;
    private Map<String, String> last = Map.of();

    // inputs: files to copy in first, for the program to read.
    public RunFolder(List<Path> inputs) throws IOException {
        path = Files.createTempDirectory("raphael-run-");
        for (Path input : inputs) {
            Files.copy(input, path.resolve(input.getFileName()));
        }
    }

    public Path path() {
        return path;
    }

    // The files in the folder now, by name: each one's text, or a note for a file too big or not
    // text. When nothing has changed, the same map as last time comes back, so the steps share it.
    public Map<String, String> files() {
        Map<String, String> now = new TreeMap<>();
        try (Stream<Path> listing = Files.list(path)) {
            listing.filter(Files::isRegularFile).sorted().limit(MAX_FILES).forEach(file -> now.put(file.getFileName().toString(), read(file)));
        } catch (IOException | UncheckedIOException e) {
            return last; // the program is changing the folder right now; the next step will see it
        }
        if (now.equals(last)) {
            return last;
        }
        last = Collections.unmodifiableMap(now);
        return last;
    }

    private static String read(Path file) {
        try {
            long size = Files.size(file);
            if (size > MAX_SHOWN_BYTES) {
                return "(" + size + " bytes: too big to show)";
            }
            byte[] bytes = Files.readAllBytes(file);
            for (byte b : bytes) {
                if (b == 0) {
                    return "(" + size + " bytes: not text)";
                }
            }
            return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPLACE)
                    .onUnmappableCharacter(CodingErrorAction.REPLACE)
                    .decode(java.nio.ByteBuffer.wrap(bytes)).toString();
        } catch (IOException e) {
            return "(couldn't be read: " + e.getMessage() + ")";
        }
    }

    // Deletes the folder and everything the program left in it.
    @Override
    public void close() {
        try (Stream<Path> all = Files.walk(path)) {
            all.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        } catch (IOException e) {
            // a file still open on Windows: it's in the temp folder, which the system cleans
        }
    }
}
