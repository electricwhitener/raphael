package raphael.compile;

import java.nio.file.Path;
import java.util.List;

// What came out of one compile: did it work, what went wrong, where the .class files are,
// and which class has main.
// OOP: a record is a compact, unchangeable class. Java makes its fields private and final and
// writes the constructor and getters for us (encapsulation). CompileError is a nested record.
public record CompileResult(boolean success, List<CompileError> errors, Path classDir, String mainClass) {

    // One compile error: the line it's on (0 if it isn't about one line) and the message.
    public record CompileError(long line, String message) {}
}
