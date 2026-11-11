package raphael.compile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import raphael.model.SourceFile;
import raphael.toolchain.Toolchains;

// Compiles a C program with gcc into an executable the debugger can step through, and turns
// gcc's messages into Raphael's compile errors.
// The result's classDir is the build folder (the code is in its src folder) and its mainClass is
// the executable's name there ("program", or "program.exe" on Windows).
// OOP: encapsulation (callers only see compile() and compileFolder()); the same CompileResult
// as Java's compiler, so the window treats both alike.
public class CCompiler {

    // -g keeps names and lines; -O0 and -fno-inline keep every line and call where the code has it.
    // The -Werror flags make every gcc stop where gcc 14 and later do (and Apple's clang does), so a
    // program compiles the same everywhere; -std=gnu17, since gcc 15 moved to C23.
    private static final List<String> FLAGS = List.of("-g", "-O0", "-fno-inline", "-fno-omit-frame-pointer",
            "-std=gnu17", "-fdiagnostics-color=never", "-Werror=implicit-function-declaration", "-Werror=implicit-int",
            "-Werror=incompatible-pointer-types", "-Werror=int-conversion");
    private static final String SINGLE = "main.c"; // the editor's one text, on disk
    private static final String EXE = Toolchains.WINDOWS ? "program.exe" : "program";
    private static final long TIMEOUT_S = 60;

    // "main.c:4:5: error: expected ';' before 'printf'"
    private static final Pattern DIAGNOSTIC = Pattern.compile("^(.+?):(\\d+):(?:\\d+:)? (fatal error|error|warning|note): (.*)$");
    // the GNU linker: "/tmp/x/main.c:2:(.text+0xe): undefined reference to `helper'",
    // "/tmp/x/b.c:1: multiple definition of `main'; …", "(.text+0x1b): undefined reference to `main'"
    private static final Pattern LINK = Pattern.compile(
            "^(?:(\\S+?):(\\d+):(?:\\([^)]*\\):)? )?.*?(undefined reference to|multiple definition of) [`'](\\w+)'");
    // Apple's linker: "  "_helper", referenced from:"
    private static final Pattern APPLE_LINK = Pattern.compile("^\\s+\"_(\\w+)\", referenced from:");
    // gcc's warning names at the end of a message: " [-Wimplicit-function-declaration]"
    private static final Pattern FLAG_NAME = Pattern.compile("\\s*\\[-W[^\\]]*\\]$");

    private static Path[] runtime; // raphael_rt.h and raphael_rt.o, compiled once per run of Raphael

    // A program typed into the editor (or opened as one file).
    public CompileResult compile(String code) throws CompileSetupException {
        Path dir = tempFolder();
        write(dir.resolve("src").resolve(SINGLE), code);
        raphael.analysis.CodeFacts facts = new raphael.analysis.CodeFacts();
        raphael.analysis.CFactScanner.scan(facts, code, null, "main"); // one text: its frames have no file name, and its unit is main.c's
        return build(dir, List.of(SINGLE), Map.of(), facts);
    }

    // A program of several files, from a folder; each path is relative to the folder. Every .c file
    // is compiled and linked (preferredMain doesn't matter in C: there's only one main).
    public CompileResult compileFolder(List<SourceFile> files, String preferredMain) throws CompileSetupException {
        Path dir = tempFolder();
        Path src = dir.resolve("src");
        List<String> sources = new ArrayList<>();
        Map<String, String> sourceFiles = new LinkedHashMap<>();
        raphael.analysis.CodeFacts facts = new raphael.analysis.CodeFacts();
        for (SourceFile file : files) {
            Path onDisk = src.resolve(file.path()).normalize();
            if (!onDisk.startsWith(src)) {
                continue; // a path like ../x.c can't leave the folder
            }
            write(onDisk, file.text());
            sourceFiles.put(file.path(), file.path());
            if (file.path().endsWith(".c")) {
                sources.add(file.path());
            }
            String name = file.path().substring(file.path().lastIndexOf('/') + 1);
            raphael.analysis.CFactScanner.scan(facts, file.text(), file.path(), name.contains(".") ? name.substring(0, name.lastIndexOf('.')) : name);
        }
        return build(dir, sources, sourceFiles, facts);
    }

    private CompileResult build(Path dir, List<String> sources, Map<String, String> sourceFiles,
                                raphael.analysis.CodeFacts facts) throws CompileSetupException {
        Path gcc = Toolchains.find("gcc");
        if (gcc == null) {
            throw new CompileSetupException(Toolchains.howToInstall("gcc to compile C"));
        }
        // First the code on its own, so its errors are exactly gcc's (raphael_rt.h includes
        // stdlib.h, which would hide a missing #include); then with the shim, into the program.
        List<String> check = new ArrayList<>(List.of(gcc.toString(), "-fsyntax-only"));
        check.addAll(FLAGS);
        check.addAll(sources);
        Output gccSaid = run(check, dir.resolve("src"));
        if (gccSaid.exit() == 0) {
            Path[] shim = runtime(gcc);
            List<String> command = new ArrayList<>(List.of(gcc.toString()));
            command.addAll(FLAGS);
            command.addAll(List.of("-include", shim[0].toString()));
            command.addAll(sources);
            command.add(shim[1].toString());
            if (Toolchains.WINDOWS) {
                // WinLibs' gcc hands ld its link-time-optimization plugin by an unquoted path, and
                // under "C:\Program Files" the space breaks it; Raphael never uses that plugin.
                command.add("-fno-use-linker-plugin");
            }
            command.addAll(List.of("-o", dir.resolve(EXE).toString()));
            gccSaid = run(command, dir.resolve("src"));
        }
        List<CompileResult.CompileError> errors = errors(gccSaid.text(), !sourceFiles.isEmpty());
        if (gccSaid.exit() != 0 && errors.isEmpty()) { // something gcc said that isn't in a known form
            errors.add(new CompileResult.CompileError(0, gccSaid.text().strip().lines().limit(6)
                    .reduce((a, b) -> a + "\n" + b).orElse("gcc stopped without saying why.")));
        }
        errors = onTheirLine(errors, dir.resolve("src"));
        boolean ok = gccSaid.exit() == 0;
        return new CompileResult(ok, ok ? List.of() : errors, dir, ok ? EXE : null, sourceFiles, facts);
    }

    // gcc's errors (warnings left out, as for Java), each with the notes that follow it.
    // named: a program of several files, whose errors name their file.
    static List<CompileResult.CompileError> errors(String output, boolean named) {
        List<CompileResult.CompileError> errors = new ArrayList<>();
        boolean afterError = false;
        for (String line : output.lines().toList()) {
            Matcher diagnostic = DIAGNOSTIC.matcher(line);
            Matcher link = LINK.matcher(line);
            Matcher appleLink = APPLE_LINK.matcher(line);
            if (diagnostic.find()) {
                String kind = diagnostic.group(3);
                String message = FLAG_NAME.matcher(diagnostic.group(4)).replaceAll("");
                if (kind.equals("note")) {
                    if (afterError) { // "include '<stdio.h>' or provide a declaration of 'printf'"
                        CompileResult.CompileError last = errors.remove(errors.size() - 1);
                        errors.add(new CompileResult.CompileError(last.file(), last.line(), last.message() + "\n" + message));
                    }
                    continue;
                }
                afterError = !kind.equals("warning");
                if (afterError) {
                    errors.add(new CompileResult.CompileError(named ? diagnostic.group(1) : null,
                            Long.parseLong(diagnostic.group(2)), message));
                }
            } else if (link.find()) {
                errors.add(linkError(link.group(4), link.group(3).startsWith("multiple"),
                        named && link.group(1) != null ? Path.of(link.group(1)).getFileName().toString() : null,
                        link.group(2) == null ? 0 : Long.parseLong(link.group(2))));
                afterError = false;
            } else if (appleLink.find()) {
                errors.add(linkError(appleLink.group(1), false, null, 0));
                afterError = false;
            }
        }
        return errors;
    }

    private static final Pattern SEMICOLON_BEFORE = Pattern.compile("^expected (?:',' or )?';' before ");

    // gcc reports a missing ; on the line after it (where it found what came next): such an error
    // moves back to the last line with code before it, where the ; belongs.
    static List<CompileResult.CompileError> onTheirLine(List<CompileResult.CompileError> errors, Path src) {
        List<CompileResult.CompileError> moved = new ArrayList<>();
        for (CompileResult.CompileError error : errors) {
            if (error.line() > 1 && SEMICOLON_BEFORE.matcher(error.message()).find()) {
                try {
                    List<String> lines = Files.readAllLines(src.resolve(error.file() == null ? SINGLE : error.file()));
                    int line = (int) error.line() - 1;
                    while (line > 1 && lines.get(line - 1).strip().isEmpty()) {
                        line--;
                    }
                    moved.add(new CompileResult.CompileError(error.file(), line, error.message()));
                    continue;
                } catch (IOException | RuntimeException e) {
                    // the file can't be read back: the error stays where gcc put it
                }
            }
            moved.add(error);
        }
        return moved;
    }

    private static CompileResult.CompileError linkError(String name, boolean twice, String file, long line) {
        if (name.equals("main") || name.equals("WinMain")) { // Windows looks for WinMain when there's no main
            return new CompileResult.CompileError(file, 0, twice ? "main is written more than once" : "the program has no main function");
        }
        return new CompileResult.CompileError(file, line, twice ? name + " is written more than once"
                : "undefined reference to " + name);
    }

    // The shim: its header, and its code compiled once (without -g, so the debugger never steps
    // into it; the tracer reads its table by the symbols' names).
    private static synchronized Path[] runtime(Path gcc) throws CompileSetupException {
        if (runtime != null && Files.exists(runtime[0]) && Files.exists(runtime[1])) {
            return runtime;
        }
        Path dir = tempFolder();
        Path header = dir.resolve("raphael_rt.h");
        Path source = dir.resolve("raphael_rt.c");
        for (Path file : new Path[] {header, source}) {
            try (InputStream in = CCompiler.class.getResourceAsStream("/c/" + file.getFileName())) {
                write(file, new String(in.readAllBytes(), StandardCharsets.UTF_8));
            } catch (IOException | NullPointerException e) {
                throw new CompileSetupException("Raphael's C runtime file is missing from the app.", e);
            }
        }
        Path object = dir.resolve("raphael_rt.o");
        Output said = run(List.of(gcc.toString(), "-O1", "-c", source.toString(), "-o", object.toString()), dir);
        if (said.exit() != 0) {
            throw new CompileSetupException("gcc couldn't compile Raphael's C runtime: " + said.text().strip());
        }
        runtime = new Path[] {header, object};
        return runtime;
    }

    private record Output(int exit, String text) {
    }

    // Runs gcc in folder, in English (LC_ALL=C), and returns what it printed.
    private static Output run(List<String> command, Path folder) throws CompileSetupException {
        ProcessBuilder builder = new ProcessBuilder(command).directory(folder.toFile()).redirectErrorStream(true);
        builder.environment().put("LC_ALL", "C");
        Toolchains.prepare(builder.environment());
        try {
            Process gcc = builder.start();
            gcc.getOutputStream().close();
            String text = new String(gcc.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (!gcc.waitFor(TIMEOUT_S, TimeUnit.SECONDS)) {
                gcc.destroyForcibly();
                throw new CompileSetupException("gcc took more than " + TIMEOUT_S + " seconds and was stopped.");
            }
            return new Output(gcc.exitValue(), text);
        } catch (IOException e) {
            throw new CompileSetupException("Raphael couldn't start gcc (" + command.get(0) + ").", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CompileSetupException("Compiling was interrupted.", e);
        }
    }

    private static Path tempFolder() throws CompileSetupException {
        try {
            return Files.createTempDirectory("raphael-c");
        } catch (IOException e) {
            throw new CompileSetupException("Couldn't write the code to the temporary folder ("
                    + System.getProperty("java.io.tmpdir") + "). Is the disk full?", e);
        }
    }

    private static void write(Path file, String text) throws CompileSetupException {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, text.replace(' ', ' ').replace('​', ' ')); // web pages' invisible characters
        } catch (IOException e) {
            throw new CompileSetupException("Couldn't write the code to the temporary folder ("
                    + System.getProperty("java.io.tmpdir") + "). Is the disk full?", e);
        }
    }
}
