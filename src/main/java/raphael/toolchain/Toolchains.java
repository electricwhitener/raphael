package raphael.toolchain;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

// Finds the programs Raphael runs for languages other than Java: gcc now, and gdb, lldb and
// python later in Stage 14. For now it looks on the PATH; the copies bundled with the installers
// (Stage 14, step 8) will be looked for first.
// OOP: a utility class (static methods, private constructor).
public final class Toolchains {

    public static final boolean WINDOWS = System.getProperty("os.name").toLowerCase(Locale.ROOT).startsWith("windows");
    public static final boolean MAC = System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("mac");

    private Toolchains() {
    }

    // The program called name ("gcc"), or null if it isn't there.
    public static Path find(String name) {
        String file = WINDOWS ? name + ".exe" : name;
        String path = System.getenv("PATH");
        if (path == null) {
            return null;
        }
        for (String folder : path.split(File.pathSeparator)) {
            if (folder.isEmpty()) {
                continue;
            }
            Path candidate = Path.of(folder, file);
            if (Files.isRegularFile(candidate) && Files.isExecutable(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private static Boolean gdbHasPython; // asked once per run of Raphael

    // Whether gdb is there and can run Python, which Raphael's C tracer is written in.
    public static synchronized boolean gdbHasPython() {
        if (gdbHasPython == null) {
            gdbHasPython = false;
            Path gdb = find("gdb");
            if (gdb != null) {
                try {
                    Process p = new ProcessBuilder(gdb.toString(), "-q", "-batch", "-nx", "-ex", "python print('raphael' + '-ok')")
                            .redirectErrorStream(true).start();
                    p.getOutputStream().close();
                    String said = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                    gdbHasPython = p.waitFor(10, java.util.concurrent.TimeUnit.SECONDS) && said.contains("raphael-ok");
                } catch (java.io.IOException e) {
                    gdbHasPython = false;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        return gdbHasPython;
    }

    // What to do when a tool is missing, in a sentence for this system.
    public static String howToInstall(String what) {
        if (WINDOWS) {
            return "Raphael couldn't find " + what + ". Reinstalling Raphael puts it back.";
        }
        if (MAC) {
            return "Raphael needs Apple's Command Line Tools for " + what + ". Type xcode-select --install in Terminal.";
        }
        return "Raphael needs " + what + ". On Ubuntu or Debian, type sudo apt install gcc gdb in a terminal.";
    }
}
