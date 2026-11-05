package raphael;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import raphael.compile.CompileResult;
import raphael.compile.SourceCompiler;
import raphael.model.EndReason;
import raphael.model.Trace;
import raphael.render.SceneBuilder;
import raphael.trace.JdiTracer;

// "Raphael --self-test": checks, with no window, that this copy of Raphael can do its job: compile
// an example, record it in a second JVM, and lay out its pictures. The installers are checked
// with it on every system, because a packaged Raphael carries its own Java, and that Java must
// include the compiler and the debugger. Exit code 0 means it all worked.
// OOP: a utility class (static methods, private constructor); exception handling: any failure,
// checked or not, becomes a message and exit code 1.
public final class SelfTest {

    private SelfTest() {
    }

    public static int run() {
        try (InputStream example = SelfTest.class.getResourceAsStream("/examples/BubbleSort.java")) {
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
            System.out.println("self-test ok: Java " + System.getProperty("java.version") + ", "
                    + trace.size() + " steps recorded, " + pictures + " pictures");
            return 0;
        } catch (Exception | LinkageError e) {
            return fail(e.toString());
        }
    }

    private static int fail(String why) {
        System.out.println("self-test failed: " + why);
        return 1;
    }
}
