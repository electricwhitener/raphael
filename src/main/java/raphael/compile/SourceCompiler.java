package raphael.compile;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import raphael.model.SourceFile;

// Turns the student's source code into .class files: one piece of text from the editor, or the
// files of a folder (a program of several classes, maybe in packages).
// Text from the editor has no file name of its own, so two rules of Java files don't apply to
// it: javac wants a public class to match its file's name, and a package to match its folder. So
// before compiling, those words are blanked out. Spaces replace them, one for one, so every error
// still points at the right line. The editor's text isn't changed. A folder's files have real
// names, so they keep their public classes and packages.
// The sources go in one temporary folder and the classes in another, and javac and the main-class
// search see only those classes, never Raphael's own (a student class named raphael.App stays the
// student's).
// OOP: encapsulation. Callers only see compile() and compileFolder(); the rest is private.
// Overloading: two ways in, one way through (compileFiles).
public class SourceCompiler {

    private static final String TYPE_WORD = "\\b(?:class|interface|enum|record)\\s+(\\w+)";
    private static final Pattern PUBLIC_TYPE = Pattern.compile("\\bpublic\\b[^;{}]*?" + TYPE_WORD);
    private static final Pattern ANY_TYPE = Pattern.compile(TYPE_WORD);
    private static final Pattern FILE_RULES = Pattern.compile("\\bpublic\\b|\\bpackage\\s+[\\w.]+\\s*;");
    private static final Pattern PACKAGE = Pattern.compile("\\bpackage\\s+([\\w.]+)\\s*;");

    // A program typed into the editor (or opened as one file).
    public CompileResult compile(String source) throws CompileSetupException {
        // javac needs a real file, named after the public class.
        String topLevel = topLevelText(source);
        String className = findClassName(topLevel);
        Path dir = tempFolder();
        Path file = dir.resolve("src").resolve(className + ".java");
        Map<Path, SourceFile> written = new LinkedHashMap<>();
        write(file, withoutFileRules(source, topLevel));
        written.put(file, null); // one text with no file of its own: its errors have no file name
        Map<String, String> sourceFiles = Map.of(); // one file: its frames have no file name (null), like the editor's one text
        return compileFiles(written, dir.resolve("classes"), className, sourceFiles);
    }

    // A program of several files, from a folder. Each file's path is relative to the folder.
    // preferredMain: the class to run if it has main (the file in front), or null.
    public CompileResult compileFolder(List<SourceFile> files, String preferredMain) throws CompileSetupException {
        Path dir = tempFolder();
        Map<Path, SourceFile> written = new LinkedHashMap<>();
        Map<String, String> sourceFiles = new LinkedHashMap<>();
        for (SourceFile source : files) {
            Path file = dir.resolve("src").resolve(source.path()).normalize();
            if (!file.startsWith(dir.resolve("src"))) {
                continue; // a path like ../x.java can't leave the folder
            }
            write(file, source.text().replace('\u00a0', ' ').replace('\u200b', ' '));
            written.put(file, source);
            Matcher pkg = PACKAGE.matcher(topLevelText(source.text()));
            String name = Path.of(source.path()).getFileName().toString();
            sourceFiles.put(pkg.find() ? pkg.group(1).replace('.', '/') + "/" + name : name, source.path());
        }
        return compileFiles(written, dir.resolve("classes"), preferredMain, sourceFiles);
    }

    private static Path tempFolder() throws CompileSetupException {
        try {
            return Files.createTempDirectory("raphael");
        } catch (IOException e) {
            throw new CompileSetupException("Couldn't write the code to the temporary folder ("
                    + System.getProperty("java.io.tmpdir") + "). Is the disk full?", e);
        }
    }

    private static void write(Path file, String text) throws CompileSetupException {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, text);
        } catch (IOException e) {
            throw new CompileSetupException("Couldn't write the code to the temporary folder ("
                    + System.getProperty("java.io.tmpdir") + "). Is the disk full?", e);
        }
    }

    // Compiles the written files into classes. written: each file on disk → the program's file
    // it was copied from (null for text from the editor).
    private CompileResult compileFiles(Map<Path, SourceFile> written, Path classes, String preferredMain,
                                       Map<String, String> sourceFiles) throws CompileSetupException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new CompileSetupException("No Java compiler found. Raphael needs a JDK, not a JRE.");
        }
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        raphael.analysis.CodeFacts facts = new raphael.analysis.CodeFacts();
        boolean ok;
        // The files were written as UTF-8, so javac reads them as UTF-8 on every computer, and it
        // reports errors in English, like the rest of Raphael, whatever the computer's language.
        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, Locale.ENGLISH, StandardCharsets.UTF_8)) {
            Files.createDirectories(classes);
            Iterable<? extends JavaFileObject> files = fileManager.getJavaFileObjectsFromPaths(written.keySet());
            // -g keeps variable names; -d puts the .class files in our temp folder; the class
            // path is that folder alone, so Raphael's own classes are out of sight.
            List<String> options = List.of("-g", "-d", classes.toString(), "-classpath", classes.toString());
            com.sun.source.util.JavacTask task = (com.sun.source.util.JavacTask)
                    compiler.getTask(null, fileManager, diagnostics, options, null, files);
            // As soon as javac has worked out what each file's names mean, FactScanner reads its
            // syntax tree into the facts: no second compile is needed.
            com.sun.source.util.Trees trees = com.sun.source.util.Trees.instance(task);
            task.addTaskListener(new com.sun.source.util.TaskListener() {
                @Override
                public void finished(com.sun.source.util.TaskEvent event) {
                    if (event.getKind() == com.sun.source.util.TaskEvent.Kind.ANALYZE && event.getCompilationUnit() != null) {
                        SourceFile from = written.get(Path.of(event.getSourceFile().toUri()));
                        raphael.analysis.FactScanner.scan(facts, trees, event.getCompilationUnit(), from == null ? null : from.path());
                    }
                }
            });
            ok = task.call();
        } catch (IOException e) { // making the folder, or closing the file manager, failed
            throw new CompileSetupException("The compiler couldn't read or write its files.", e);
        }

        // Keep only errors; warnings don't stop the program from running.
        List<CompileResult.CompileError> errors = new ArrayList<>();
        for (Diagnostic<? extends JavaFileObject> d : diagnostics.getDiagnostics()) {
            if (d.getKind() == Diagnostic.Kind.ERROR) {
                errors.add(new CompileResult.CompileError(fileOf(d, written), d.getLineNumber(), d.getMessage(Locale.ENGLISH)));
            }
        }

        // The class to run is the one with main, which isn't always the public one.
        String mainClass = preferredMain;
        if (ok) {
            try {
                mainClass = findMainClass(classes, preferredMain);
            } catch (IOException e) {
                throw new CompileSetupException("Couldn't read the compiled classes back.", e);
            }
            if (mainClass == null) {
                ok = false;
                errors.add(new CompileResult.CompileError(0,
                        "No main method found. Add: public static void main(String[] args)"));
            }
        }
        return new CompileResult(ok, errors, classes, mainClass, sourceFiles, facts);
    }

    // The program's file an error is in, or null (text from the editor, or no file at all).
    private static String fileOf(Diagnostic<? extends JavaFileObject> d, Map<Path, SourceFile> written) {
        if (d.getSource() == null) {
            return null;
        }
        try {
            SourceFile source = written.get(Path.of(d.getSource().toUri()));
            return source == null ? null : source.path();
        } catch (IllegalArgumentException | java.nio.file.FileSystemNotFoundException e) {
            return null;
        }
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

    // Loads the compiled top-level classes (without running any of their code) and returns the
    // name of one with public static void main(String[]): the preferred class if it has one,
    // otherwise the first in name order. Null if none has.
    // The loader's parent is the platform's, not Raphael's, so only the student's classes are seen.
    private String findMainClass(Path classes, String preferred) throws IOException {
        List<String> candidates = new ArrayList<>();
        if (preferred != null) {
            candidates.add(preferred);
        }
        try (java.util.stream.Stream<Path> all = Files.walk(classes)) {
            all.map(classes::relativize).map(Path::toString)
                    .filter(name -> name.endsWith(".class") && !name.contains("$")) // Main$Node is a class inside Main
                    .map(name -> name.substring(0, name.length() - ".class".length()).replace(java.io.File.separatorChar, '.'))
                    .sorted()
                    .forEach(candidates::add);
        }
        try (URLClassLoader loader = new URLClassLoader(new URL[] {classes.toUri().toURL()}, ClassLoader.getPlatformClassLoader())) {
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
