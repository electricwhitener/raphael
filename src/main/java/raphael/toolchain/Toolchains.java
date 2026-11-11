package raphael.toolchain;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

// Finds the programs Raphael runs for languages other than Java: gcc and gdb (lldb on a Mac), and
// later python. The Windows installer carries its own (WinLibs, in a mingw64 folder next to
// Raphael's jar), looked for first; elsewhere, and in a copy built from the code, they're the
// system's, found on the PATH.
// OOP: a utility class (static methods, private constructor).
public final class Toolchains {

    public static final boolean WINDOWS = System.getProperty("os.name").toLowerCase(Locale.ROOT).startsWith("windows");
    public static final boolean MAC = System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("mac");

    private Toolchains() {
    }

    // The bin folder of the toolchain that came with Raphael, or null.
    public static Path bundled() {
        try {
            Path jar = Path.of(Toolchains.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            Path bin = jar.getParent().resolve("mingw64").resolve("bin");
            return Files.isDirectory(bin) ? bin : null;
        } catch (java.net.URISyntaxException | RuntimeException e) {
            return null;
        }
    }

    // For a process that runs one of these tools: the bundled toolchain's bin first on its PATH,
    // so gcc finds its assembler and linker, and gdb its DLLs and Python, there.
    public static void prepare(java.util.Map<String, String> environment) {
        Path bin = bundled();
        if (bin != null) {
            String key = environment.keySet().stream().filter(k -> k.equalsIgnoreCase("PATH")).findFirst().orElse("PATH");
            environment.put(key, bin + File.pathSeparator + environment.getOrDefault(key, ""));
        }
    }

    // The program called name ("gcc"), or null if it isn't there: Raphael's own copy first.
    public static Path find(String name) {
        String file = WINDOWS ? name + ".exe" : name;
        Path bin = bundled();
        if (bin != null && Files.isRegularFile(bin.resolve(file))) {
            return bin.resolve(file);
        }
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

    private static Boolean debuggerHasPython; // asked once per run of Raphael

    // The debugger Raphael traces C with here: lldb on a Mac (Apple's tools have no gdb), gdb elsewhere.
    public static String debugger() {
        return MAC ? "lldb" : "gdb";
    }

    // Whether that debugger is there and can run Python, which Raphael's C tracer is written in.
    public static synchronized boolean debuggerHasPython() {
        if (debuggerHasPython == null) {
            debuggerHasPython = false;
            Path debugger = find(debugger());
            if (debugger != null) {
                java.util.List<String> command = MAC
                        ? java.util.List.of(debugger.toString(), "--batch", "--no-lldbinit", "-o", "script print('raphael' + '-ok')")
                        : java.util.List.of(debugger.toString(), "-q", "-batch", "-nx", "-ex", "python print('raphael' + '-ok')");
                try {
                    ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true);
                    prepare(builder.environment());
                    Process p = builder.start();
                    p.getOutputStream().close();
                    String said = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                    debuggerHasPython = p.waitFor(20, java.util.concurrent.TimeUnit.SECONDS) && said.contains("raphael-ok");
                } catch (java.io.IOException e) {
                    debuggerHasPython = false;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        return debuggerHasPython;
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
