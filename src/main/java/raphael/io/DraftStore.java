package raphael.io;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

// Autosave. While Raphael runs, the editor's code is copied to one draft file every few
// seconds. A normal quit deletes the draft. So if a draft is there when Raphael starts, the
// last session ended without quitting (a crash, a power cut, "End task"), and the code in it
// can be offered back.
// OOP: encapsulation (the file is private; save, load and clear are the only ways in).
public class DraftStore {

    private final Path file;

    public DraftStore(Path file) {
        this.file = file;
    }

    public void save(String code) throws IOException {
        SafeFiles.write(file, code);
    }

    // The draft's code, or null if there is none.
    public String load() throws IOException {
        return Files.exists(file) ? Files.readString(file, StandardCharsets.UTF_8) : null;
    }

    public void clear() throws IOException {
        Files.deleteIfExists(file);
    }
}
