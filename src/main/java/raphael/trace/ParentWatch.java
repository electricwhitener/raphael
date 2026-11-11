package raphael.trace;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

// Makes sure the student's program never outlives Raphael.
// When Raphael closes normally, JdiTracer ends the program itself. But if Raphael is killed
// outright (Task Manager's "End task", or a crash), none of its code runs any more. So the
// program's JVM also gets this class as a Java agent: Java runs an agent's premain() before
// main(). premain() asks to be told when its parent process (Raphael) ends, and then halts the
// program. While the program is paused at a breakpoint this can't run, but when Raphael's debug
// connection drops, Java resumes the program, and then it does.
// An agent must be in a jar file whose manifest names it, so jar() writes that small jar.
// OOP: static members; exception handling: try-with-resources closes both streams; file handling:
// the jar is written with JarOutputStream, a stream that packs entries into a zip file.
public final class ParentWatch {

    private static Path jar; // made once per run of Raphael

    private ParentWatch() {
    }

    // Runs inside the student's program, before its main. args: Raphael's process id.
    // Raphael is named by its id, not found as this program's parent: if Raphael died while the
    // program was starting, the system has already made another process (launchd, init) its
    // parent, which never ends. A Raphael that's already gone ends the program at once.
    // Raphael isn't this program's child's parent in Java's eyes (Java can only wait on its own
    // children), so ProcessHandle.onExit() would only poll, waiting longer and longer between
    // looks. A small thread of our own looks five times a second instead.
    public static void premain(String args) {
        java.util.Optional<ProcessHandle> named;
        try {
            named = args == null || args.isBlank() ? ProcessHandle.current().parent() : ProcessHandle.of(Long.parseLong(args.trim()));
        } catch (NumberFormatException e) {
            named = ProcessHandle.current().parent();
        }
        if (named.isEmpty() || !named.get().isAlive()) {
            Runtime.getRuntime().halt(1); // Raphael is already gone
        }
        named.ifPresent(raphael -> {
            Thread watch = new Thread(() -> {
                while (raphael.isAlive()) {
                    try {
                        Thread.sleep(200);
                    } catch (InterruptedException e) {
                        return;
                    }
                }
                Runtime.getRuntime().halt(1);
            }, "raphael-parent-watch");
            watch.setDaemon(true); // never keeps the program running by itself
            watch.start();
        });
    }

    // The agent jar: a manifest naming this class, and this class's own compiled bytes.
    static synchronized Path jar() throws IOException {
        if (jar != null && Files.exists(jar)) {
            return jar;
        }
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().put(new Attributes.Name("Premain-Class"), ParentWatch.class.getName());
        String entry = ParentWatch.class.getName().replace('.', '/') + ".class";
        Path file = Files.createTempFile("raphael-watch", ".jar");
        try (InputStream bytes = ParentWatch.class.getResourceAsStream("/" + entry);
             JarOutputStream out = new JarOutputStream(Files.newOutputStream(file), manifest)) {
            if (bytes == null) {
                throw new IOException("Can't find " + entry);
            }
            out.putNextEntry(new JarEntry(entry));
            bytes.transferTo(out);
        }
        file.toFile().deleteOnExit();
        jar = file;
        return jar;
    }
}
