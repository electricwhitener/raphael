package raphael.compile;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

// Turns the student's source code (a String from the editor) into .class files.
// The code is one piece of text with no file name of its own, so two rules of Java files don't
// apply to it: javac wants a public class to match its file's name, and a package to match its
// folder. So before compiling, those words are blanked out. Spaces replace them, one for one, so
// every error still points at the right line. The editor's text isn't changed.
// OOP: encapsulation. Callers only see compile(); the patterns and helper methods are private.
public class SourceCompiler {

    private static final String TYPE_WORD = "\\b(?:class|interface|enum|record)\\s+(\\w+)";
    private static final Pattern PUBLIC_TYPE = Pattern.compile("\\bpublic\\b[^;{}]*?" + TYPE_WORD);
    private static final Pattern ANY_TYPE = Pattern.compile(TYPE_WORD);
    private static final Pattern FILE_RULES = Pattern.compile("\\bpublic\\b|\\bpackage\\s+[\\w.]+\\s*;");

    public CompileResult compile(String source) throws CompileSetupException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new CompileSetupException("No Java compiler found. Raphael needs a JDK, not a JRE.");
        }

        // javac needs a real file, named after the public class.
        String topLevel = topLevelText(source);
        String className = findClassName(topLevel);
        Path dir;
        Path file;
        try {
            dir = Files.createTempDirectory("raphael");
            file = dir.resolve(className + ".java");
            Files.writeString(file, withoutFileRules(source, topLevel));
        } catch (IOException e) {
            throw new CompileSetupException("Couldn't write the code to the temporary folder ("
                    + System.getProperty("java.io.tmpdir") + "). Is the disk full?", e);
        }

        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        boolean ok;
        // The file was written as UTF-8, so javac reads it as UTF-8 on every computer, and it
        // reports errors in English, like the rest of Raphael, whatever the computer's language.
        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, Locale.ENGLISH, StandardCharsets.UTF_8)) {
            Iterable<? extends JavaFileObject> files = fileManager.getJavaFileObjects(file);
            // -g keeps variable names; -d puts the .class files in our temp folder.
            List<String> options = List.of("-g", "-d", dir.toString());
            ok = compiler.getTask(null, fileManager, diagnostics, options, null, files).call();
        } catch (IOException e) { // closing the file manager failed
            throw new CompileSetupException("The compiler couldn't read or write its files.", e);
        }

        // Keep only errors; warnings don't stop the program from running.
        List<CompileResult.CompileError> errors = new ArrayList<>();
        for (Diagnostic<? extends JavaFileObject> d : diagnostics.getDiagnostics()) {
            if (d.getKind() == Diagnostic.Kind.ERROR) {
                errors.add(new CompileResult.CompileError(d.getLineNumber(), d.getMessage(Locale.ENGLISH)));
            }
        }

        // The class to run is the one with main, which isn't always the public one.
        String mainClass = className;
        if (ok) {
            try {
                mainClass = findMainClass(dir, className);
            } catch (IOException e) {
                throw new CompileSetupException("Couldn't read the compiled classes back.", e);
            }
            if (mainClass == null) {
                ok = false;
                errors.add(new CompileResult.CompileError(0,
                        "No main method found. Add: public static void main(String[] args)"));
            }
        }
        return new CompileResult(ok, errors, dir, mainClass);
    }

    // The code with only its top level left: comments, strings and everything inside { } become
    // spaces, so "class" in a comment or "public" on a nested class can't be mistaken for the real
    // thing. It has the same length as the code, so a position in one is a position in the other.
    private static String topLevelText(String source) {
        char[] kept = new char[source.length()];
        int depth = 0;
        int i = 0;
        while (i < source.length()) {
            int end = endOfCommentOrText(source, i);
            if (end > i) {
                for (int k = i; k < end; k++) {
                    kept[k] = source.charAt(k) == '\n' ? '\n' : ' ';
                }
                i = end;
                continue;
            }
            char c = source.charAt(i);
            depth = c == '{' ? depth + 1 : c == '}' ? Math.max(0, depth - 1) : depth;
            kept[i] = c == '\n' || (depth == 0 && c != '}') ? c : ' ';
            i++;
        }
        return new String(kept);
    }

    // If a comment, string, text block or char starts at position i, where it ends; otherwise i.
    private static int endOfCommentOrText(String s, int i) {
        if (s.startsWith("//", i)) {
            int newline = s.indexOf('\n', i);
            return newline < 0 ? s.length() : newline;
        }
        if (s.startsWith("/*", i)) {
            int close = s.indexOf("*/", i + 2);
            return close < 0 ? s.length() : close + 2;
        }
        if (s.startsWith("\"\"\"", i)) {
            int close = s.indexOf("\"\"\"", i + 3);
            return close < 0 ? s.length() : close + 3;
        }
        char quote = s.charAt(i);
        if (quote != '"' && quote != '\'') {
            return i;
        }
        int k = i + 1;
        while (k < s.length() && s.charAt(k) != quote && s.charAt(k) != '\n') {
            k += s.charAt(k) == '\\' ? 2 : 1;
        }
        return Math.min(k + 1, s.length());
    }

    // Prefer the public type; otherwise the first type; otherwise "Main".
    private static String findClassName(String topLevel) {
        Matcher m = PUBLIC_TYPE.matcher(topLevel);
        if (m.find()) {
            return m.group(1);
        }
        m = ANY_TYPE.matcher(topLevel);
        return m.find() ? m.group(1) : "Main";
    }

    // The text javac is given: no top-level "public" or "package", and no invisible characters
    // copied from a web page (a non-breaking space or a zero-width space), which javac rejects.
    private static String withoutFileRules(String source, String topLevel) {
        char[] text = source.replace(' ', ' ').replace('​', ' ').toCharArray();
        Matcher m = FILE_RULES.matcher(topLevel.replace(' ', ' ').replace('​', ' '));
        while (m.find()) {
            Arrays.fill(text, m.start(), m.end(), ' ');
        }
        return new String(text);
    }

    // Loads each compiled top-level class (without running any of its code) and returns the
    // first one that has public static void main(String[]), or null if none does.
    private String findMainClass(Path dir, String preferred) throws IOException {
        List<String> candidates = new ArrayList<>();
        candidates.add(preferred);
        try (DirectoryStream<Path> classFiles = Files.newDirectoryStream(dir, "*.class")) {
            for (Path classFile : classFiles) {
                String name = classFile.getFileName().toString().replace(".class", "");
                if (!name.contains("$")) { // Main$Node.class is a class nested inside Main
                    candidates.add(name);
                }
            }
        }
        try (URLClassLoader loader = new URLClassLoader(new URL[] {dir.toUri().toURL()})) {
            for (String name : candidates) {
                try {
                    Method main = Class.forName(name, false, loader).getDeclaredMethod("main", String[].class);
                    if (Modifier.isPublic(main.getModifiers()) && Modifier.isStatic(main.getModifiers())) {
                        return name;
                    }
                } catch (ClassNotFoundException | NoSuchMethodException | LinkageError e) {
                    // Not this class; try the next one.
                }
            }
        }
        return null;
    }
}
