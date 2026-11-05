package raphael.io;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

// Reading and writing the user's files without surprises.
// Writing goes to a temporary file next to the real one, which then replaces it in one move. If
// the computer crashes or the disk fills up halfway, the old file is still whole: a file is
// never left half-written.
// Reading refuses files that are too big for the editor or aren't text.
// OOP: a utility class; exception handling: specific IOException subclasses (NoSuchFile,
// AccessDenied, FileSystemException) are caught in order, most specific first, in describe().
public final class SafeFiles {

    public static final long MAX_CODE_BYTES = 1_000_000; // 1 MB of code is far more than one program needs

    private SafeFiles() {
    }

    public static void write(Path file, String text) throws IOException {
        Path folder = file.toAbsolutePath().getParent();
        Path temp = Files.createTempFile(folder, "." + file.getFileName() + "-", ".tmp");
        try {
            Files.writeString(temp, text, StandardCharsets.UTF_8);
            try {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING); // some network drives
            }
        } finally {
            Files.deleteIfExists(temp); // only still there if something above failed
        }
    }

    // Reads a file of code. Not Files.readString: that refuses a file that isn't valid UTF-8,
    // like one an old Notepad saved, so the bytes are decoded leniently instead.
    public static String readCode(Path file) throws IOException {
        long size = Files.size(file);
        if (size > MAX_CODE_BYTES) {
            throw new IOException("it's " + size / 1_000_000 + " MB, too big to be one program (the limit is 1 MB)");
        }
        String text = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
        if (text.indexOf('\0') >= 0) {
            throw new IOException("it isn't a text file");
        }
        return text;
    }

    // A short, plain reason for a file problem, to finish "Couldn't open X: …".
    public static String describe(IOException e) {
        if (e instanceof NoSuchFileException) {
            return "it doesn't exist (was it moved or deleted?)";
        } else if (e instanceof AccessDeniedException) {
            return "you don't have permission to use it";
        } else if (e instanceof FileSystemException && ((FileSystemException) e).getReason() != null) {
            return ((FileSystemException) e).getReason();
        }
        return e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
    }
}
