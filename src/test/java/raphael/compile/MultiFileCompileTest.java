package raphael.compile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import raphael.model.SourceFile;

// Programs of several files, as a folder holds them.
class MultiFileCompileTest {

    private final SourceCompiler compiler = new SourceCompiler();

    private static final SourceFile SHAPE = new SourceFile("shapes/Shape.java", """
            package shapes;

            public abstract class Shape {
                public abstract double area();
            }
            """);
    private static final SourceFile CIRCLE = new SourceFile("shapes/Circle.java", """
            package shapes;

            public class Circle extends Shape {
                private final double r;

                public Circle(double r) {
                    this.r = r;
                }

                public double area() {
                    return 3 * r * r;
                }
            }
            """);
    private static final SourceFile MAIN = new SourceFile("Main.java", """
            import shapes.*;

            public class Main {
                public static void main(String[] args) {
                    Shape s = new Circle(2);
                    System.out.println(s.area());
                }
            }
            """);

    // Classes in a package, in their package's folder, compile together, and Java's names for
    // the running files lead back to the program's own paths.
    @Test
    void filesInPackagesCompileTogether() throws Exception {
        CompileResult result = compiler.compileFolder(List.of(SHAPE, CIRCLE, MAIN), null);
        assertTrue(result.success(), () -> result.errors().toString());
        assertEquals("Main", result.mainClass());
        assertEquals(Map.of("shapes/Shape.java", "shapes/Shape.java", "shapes/Circle.java", "shapes/Circle.java",
                "Main.java", "Main.java"), result.sourceFiles());
    }

    // An error names the file it's in, so the right tab can show it.
    @Test
    void anErrorSaysWhichFile() throws Exception {
        SourceFile broken = new SourceFile("shapes/Circle.java", CIRCLE.text().replace("return 3 * r * r;", "return 3 * radius;"));
        CompileResult result = compiler.compileFolder(List.of(SHAPE, broken, MAIN), null);
        assertFalse(result.success());
        assertEquals("shapes/Circle.java", result.errors().get(0).file());
        assertEquals(11, result.errors().get(0).line());
    }

    // With several mains, the preferred class (the file in front) runs; without one, the first by name.
    @Test
    void thePreferredMainRuns() throws Exception {
        SourceFile other = new SourceFile("Demo.java", """
                public class Demo {
                    public static void main(String[] args) {
                    }
                }
                """);
        assertEquals("Main", compiler.compileFolder(List.of(SHAPE, CIRCLE, MAIN, other), "Main").mainClass());
        assertEquals("Demo", compiler.compileFolder(List.of(SHAPE, CIRCLE, MAIN, other), null).mainClass());
        SourceFile packaged = new SourceFile("app/Start.java", """
                package app;

                public class Start {
                    public static void main(String[] args) {
                    }
                }
                """);
        assertEquals("app.Start", compiler.compileFolder(List.of(packaged), null).mainClass());
    }

    // The student's code sees only the student's classes: a class of their own named like one of
    // Raphael's is theirs, both to javac and to the search for main.
    @Test
    void raphaelsOwnClassesAreOutOfSight() throws Exception {
        SourceFile app = new SourceFile("raphael/App.java", """
                package raphael;

                public class App {
                    public static void main(String[] args) {
                        System.out.println("the student's App");
                    }
                }
                """);
        CompileResult result = compiler.compileFolder(List.of(app), null);
        assertTrue(result.success(), () -> result.errors().toString());
        assertEquals("raphael.App", result.mainClass());
        assertTrue(java.nio.file.Files.exists(result.classDir().resolve("raphael/App.class")));
        // and a single program can't use Raphael's classes either
        CompileResult uses = compiler.compile("""
                public class Peek {
                    public static void main(String[] args) {
                        raphael.App.main(args);
                    }
                }
                """);
        assertFalse(uses.success(), "javac can't see Raphael's classes");
    }

    // A path that tries to leave the program's folder is left out.
    @Test
    void aFileCantLeaveTheFolder() throws Exception {
        SourceFile escape = new SourceFile("../Escape.java", "class Escape {}");
        CompileResult result = compiler.compileFolder(List.of(MAIN, escape), null);
        assertFalse(result.success(), "Main needs the shapes, which aren't here");
        assertFalse(result.sourceFiles().containsKey("Escape.java"));
    }
}
