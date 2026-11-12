package raphael;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import raphael.compile.CompileResult;
import raphael.compile.SourceCompiler;
import raphael.io.RunFile;
import raphael.model.EndReason;
import raphael.model.Trace;
import raphael.render.SceneBuilder;
import raphael.trace.JdiTracer;

// "Raphael --self-test": checks, with no window, that this copy of Raphael can do its job: compile
// an example, record it in a second JVM, and lay out its pictures. The installers are checked
// with it on every system, because a packaged Raphael carries its own Java, and that Java must
// include the compiler and the debugger. Then the same for C, where this computer can run it: a
// C example compiled with gcc and recorded through gdb (lldb on a Mac), which checks the Windows
// installer's own copy of them and the Linux package's dependencies. Exit code 0 means it all worked.
// OOP: a utility class (static methods, private constructor); exception handling: any failure,
// checked or not, becomes a message and exit code 1.
public final class SelfTest {

    private SelfTest() {
    }

    // needC: C must work too (the installers' checks); otherwise C is checked only where it can run.
    public static int run(boolean needC) {
        try (InputStream example = SelfTest.class.getResourceAsStream("/examples/java/sorting/BubbleSort.java")) {
            String source = new String(example.readAllBytes(), StandardCharsets.UTF_8);
            CompileResult program = new SourceCompiler().compile(source);
            if (!program.success()) {
                return fail("the example didn't compile: " + program.errors());
            }
            Trace trace = new JdiTracer().trace(program, "");
            if (trace.getEndReason() != EndReason.FINISHED || trace.size() == 0) {
                return fail("the example didn't record: " + trace.getEndMessage());
            }
            int pictures = new SceneBuilder(source).build(trace).size();
            // A recording saved and opened again (serialization works in this copy's Java).
            java.nio.file.Path saved = java.nio.file.Files.createTempFile("self-test", RunFile.EXTENSION);
            RunFile.write(saved, new RunFile.Saved(source, "", trace));
            int reopened = RunFile.read(saved).trace().size();
            java.nio.file.Files.delete(saved);
            if (reopened != trace.size()) {
                return fail("a saved recording came back with " + reopened + " steps, not " + trace.size());
            }
            System.out.println("self-test ok: Java " + System.getProperty("java.version") + ", "
                    + trace.size() + " steps recorded, " + pictures + " pictures, saved and reopened");
            return c(needC);
        } catch (Exception | LinkageError e) {
            return fail(e.toString());
        }
    }

    // The C example, then the C++ one: compiled, recorded, and printing what they should.
    private static int c(boolean needed) throws Exception {
        int c = compiled("c", "/examples/c/lists/linked_list.c", "40 30 20 10", needed);
        return c != 0 ? c : compiled("cpp", "/examples/cpp/lists/linked_list.cpp", "1 2 3", needed);
    }

    private static int compiled(String id, String example, String prints, boolean needed) throws Exception {
        raphael.lang.Language language = raphael.lang.Languages.byId(id);
        String cantRun = language.cantRun();
        if (cantRun != null) {
            if (needed) {
                return fail(language.name() + " can't run: " + cantRun);
            }
            System.out.println("self-test: " + language.name() + " isn't checked here: " + cantRun);
            return 0;
        }
        try (InputStream in = SelfTest.class.getResourceAsStream(example)) {
            String source = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            CompileResult program = language.compile(source);
            if (!program.success()) {
                return fail("the " + language.name() + " example didn't compile: " + program.errors());
            }
            Trace trace = language.newTracer().trace(program, "");
            if (trace.getEndReason() != EndReason.FINISHED || !trace.getOutput().strip().equals(prints)) {
                return fail("the " + language.name() + " example didn't record: " + trace.getEndMessage() + ", printed " + trace.getOutput().strip());
            }
            System.out.println("self-test ok: " + language.name() + ", " + trace.size() + " steps recorded with "
                    + raphael.toolchain.Toolchains.find(raphael.toolchain.Toolchains.debugger()));
            return 0;
        }
    }

    private static int fail(String why) {
        System.out.println("self-test failed: " + why);
        return 1;
    }
}
