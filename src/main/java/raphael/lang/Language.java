package raphael.lang;

import java.util.List;
import java.util.Locale;
import raphael.compile.CompileResult;
import raphael.compile.CompileSetupException;
import raphael.model.SourceFile;
import raphael.trace.Tracer;

// A programming language Raphael knows: how its files are named and its code is coloured and
// commented, what a new program starts as, whether it reads input or files, how to recognise it
// when it's pasted, and how to compile and trace it. Everything outside lang/ asks the tab's
// Language instead of assuming Java.
// OOP: an abstract class (abstraction), one subclass per language (inheritance), used through
// this type alone (polymorphism): the Strategy pattern, since each language is a different way of
// doing the same jobs. The Template Method pattern too: fileName() and owns() are written once
// here, on top of the abstract endings().
public abstract class Language {

    public abstract String id();          // "java", "c", "cpp", "python"

    public abstract String name();        // "Java", "C", "C++", "Python"

    // File endings, the usual one first: ".java"; ".c", ".h".
    public abstract List<String> endings();

    // "//" or "#": what Ctrl+/ puts at the start of a line, and what "moment:" comments start with.
    public abstract String lineComment();

    // True when a block opens with ":" and goes on while it's indented (Python), not with "{".
    public boolean blocksByIndent() {
        return false;
    }

    // What a new program starts as.
    public abstract String template();

    // A file name for code that has none yet.
    public String suggestedName(String code) {
        return "main" + endings().get(0);
    }

    public abstract List<Token> tokens(String code);

    // Whether the code reads what's typed into the stdin box.
    public abstract boolean readsInput(String code);

    // Whether the code works with files.
    public abstract boolean usesFiles(String code);

    // How much the code looks like this language, from 0 (not at all) up; for recognising a pasted program.
    public abstract int likeness(String code);

    // Null if Raphael can run this language's programs here, else a sentence saying why not.
    public abstract String cantRun();

    // Compiles one file of code, or a folder's files with the preferred main file first.
    public abstract CompileResult compile(String code) throws CompileSetupException;

    public abstract CompileResult compileFolder(List<SourceFile> files, String preferredMain) throws CompileSetupException;

    // A new tracer for one run.
    public abstract Tracer newTracer();

    // ---- written once, for every language ----

    // Whether a file of this name is this language's ("Tree.java", "list.C").
    public boolean owns(String fileName) {
        String lower = fileName.toLowerCase(Locale.ROOT);
        for (String ending : endings()) {
            if (lower.endsWith(ending)) {
                return true;
            }
        }
        return false;
    }

    // The name without this language's ending: "Tree.java" → "Tree".
    public String baseName(String fileName) {
        for (String ending : endings()) {
            if (fileName.toLowerCase(Locale.ROOT).endsWith(ending)) {
                return fileName.substring(0, fileName.length() - ending.length());
            }
        }
        return fileName;
    }

    @Override
    public String toString() {
        return name();
    }
}
