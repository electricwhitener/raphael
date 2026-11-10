package raphael.compile;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

// What came out of one compile: did it work, what went wrong, where the .class files are,
// which class has main, and which file each class came from.
// OOP: a record is a compact, unchangeable class. Java makes its fields private and final and
// writes the constructor and getters for us (encapsulation). CompileError is a nested record.
// sourceFiles: for each compiled source, its package folder and file name as Java reports them
// while it runs ("shapes/Circle.java", or "Main.java" with no package) → the program's own path
// for that file. That's how a running line is matched to its file.
// facts: what the code says (from javac's syntax tree), for the explanations; see analysis/CodeFacts.
public record CompileResult(boolean success, List<CompileError> errors, Path classDir, String mainClass,
                            Map<String, String> sourceFiles, raphael.analysis.CodeFacts facts) {

    public CompileResult(boolean success, List<CompileError> errors, Path classDir, String mainClass) {
        this(success, errors, classDir, mainClass, Map.of(), new raphael.analysis.CodeFacts());
    }

    public CompileResult(boolean success, List<CompileError> errors, Path classDir, String mainClass,
                         Map<String, String> sourceFiles) {
        this(success, errors, classDir, mainClass, sourceFiles, new raphael.analysis.CodeFacts());
    }

    // One compile error: the file it's in (null if it isn't about one file), the line (0 if it
    // isn't about one line) and the message.
    public record CompileError(String file, long line, String message) {

        public CompileError(long line, String message) {
            this(null, line, message);
        }
    }
}
