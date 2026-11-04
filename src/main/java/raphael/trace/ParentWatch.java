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

    // Runs inside the student's program, before its main.
    public static void premain(String args) {
        ProcessHandle.current().parent().ifPresent(raphael ->
                raphael.onExit().thenRun(() -> Runtime.getRuntime().halt(1)));
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
