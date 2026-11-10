package raphael.trace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import raphael.compile.CompileResult;
import raphael.compile.SourceCompiler;
import raphael.model.FrameSnapshot;
import raphael.model.SourceFile;
import raphael.model.Snapshot;
import raphael.model.ThrownException;
import raphael.model.Trace;

// A program of several files, traced: every step knows which file its line is in.
class MultiFileTraceTest {

    private static Trace trace(List<SourceFile> files) throws Exception {
        CompileResult program = new SourceCompiler().compileFolder(files, null);
        assertTrue(program.success(), () -> program.errors().toString());
        return new JdiTracer().trace(program);
    }

    @Test
    void eachStepKnowsItsFile() throws Exception {
        Trace trace = trace(List.of(
                new SourceFile("shapes/Square.java", """
                        package shapes;

                        public class Square {
                            private final int side;

                            public Square(int side) {
                                this.side = side;
                            }

                            public int area() {
                                if (side < 0) {
                                    throw new IllegalStateException("negative");
                                }
                                return side * side;
                            }
                        }
                        """),
                new SourceFile("Main.java", """
                        import shapes.Square;

                        public class Main {
                            public static void main(String[] args) {
                                int a = new Square(3).area();
                                try {
                                    new Square(-1).area();
                                } catch (IllegalStateException e) {
                                    a = 0;
                                }
                                System.out.println(a);
                            }
                        }
                        """)));
        assertEquals("0\n", trace.getOutput());
        Set<String> files = new LinkedHashSet<>();
        ThrownException caught = null;
        for (Snapshot step : trace.getSnapshots()) {
            for (FrameSnapshot frame : step.getFrames()) {
                files.add(frame.getMethodName() + " in " + frame.getFile());
            }
            caught = step.getThrown() != null ? step.getThrown() : caught;
        }
        assertEquals(Set.of("main in Main.java", "Square in shapes/Square.java", "area in shapes/Square.java"), files);
        assertEquals("shapes/Square.java", caught.throwFile(), "thrown in one file...");
        assertEquals("Main.java", caught.catchFile(), "...and caught in another");
    }

    // A student's own package named raphael is the student's code like any other: Raphael leaves
    // out only its own agent class, by its exact name.
    @Test
    void aPackageCalledRaphaelIsStillTraced() throws Exception {
        Trace trace = trace(List.of(new SourceFile("raphael/demo/Hello.java", """
                package raphael.demo;

                public class Hello {
                    public static void main(String[] args) {
                        int x = 1;
                        x++;
                        System.out.println(x);
                    }
                }
                """)));
        assertEquals("2\n", trace.getOutput());
        assertTrue(trace.size() >= 3, "a step for each line: " + trace.size());
        assertEquals("raphael/demo/Hello.java", trace.get(0).getFrames().get(0).getFile());
    }
}
