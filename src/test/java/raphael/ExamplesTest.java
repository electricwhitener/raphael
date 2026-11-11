package raphael;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import raphael.compile.CompileResult;
import raphael.io.ExampleCatalog;
import raphael.model.EndReason;
import raphael.model.Trace;
import raphael.render.SceneBuilder;

// Every example in the catalog compiles, runs to the end it's meant to have, and prints what it
// should. The outputs are in expected-output.txt, under a "## path" line each.
class ExamplesTest {

    // The examples that end some other way on purpose: they're in "Find the bug".
    private static final Map<String, EndReason> ENDINGS = Map.of(
            "OffByOne", EndReason.EXCEPTION, "NullNode", EndReason.EXCEPTION, "MissingBaseCase", EndReason.STEP_LIMIT,
            "null_pointer", EndReason.EXCEPTION);

    private Trace run(String name) throws Exception {
        ExampleCatalog.Example example = ExampleCatalog.find(name);
        String source = ExampleCatalog.source(example);
        CompileResult program = example.language().compile(source);
        assertTrue(program.success(), () -> name + " didn't compile: " + program.errors());
        Trace trace = example.language().newTracer().trace(program, ExampleCatalog.input(source));
        assertEquals(trace.size(), new SceneBuilder(source).build(trace).size());
        assertTrue(trace.size() > 0, name + " recorded no steps");
        return trace;
    }

    static Stream<String> examples() {
        return ExampleCatalog.all().stream() // the languages Raphael can run on this computer
                .filter(example -> example.language().cantRun() == null).map(ExampleCatalog.Example::name);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("examples")
    void everyExampleRunsToItsExpectedEnd(String name) throws Exception {
        Trace trace = run(name);
        assertEquals(ENDINGS.getOrDefault(name, EndReason.FINISHED), trace.getEndReason(), name);
        if (trace.getEndReason() == EndReason.FINISHED) {
            String path = ExampleCatalog.find(name).key(); // "oop/References" for Java, "c/lists/linked_list" for C
            assertEquals(expectedOutputs().get(path), trace.getOutput().trim().replace("\r\n", "\n"), path);
        }
    }

    private static Map<String, String> expected;

    private static synchronized Map<String, String> expectedOutputs() throws Exception {
        if (expected == null) {
            expected = new HashMap<>();
            String path = null;
            StringBuilder text = new StringBuilder();
            for (String line : new String(ExamplesTest.class.getResourceAsStream("/expected-output.txt").readAllBytes(),
                    StandardCharsets.UTF_8).replace("\r\n", "\n").split("\n", -1)) { // Windows' line endings, after git
                if (line.startsWith("## ")) {
                    if (path != null) {
                        expected.put(path, text.toString().trim());
                    }
                    path = line.substring(3).trim();
                    text.setLength(0);
                } else {
                    text.append(line).append("\n");
                }
            }
            expected.put(path, text.toString().trim());
        }
        return expected;
    }

    // Each example file is in the catalog, once, so none is left out of the menu by mistake.
    @Test
    void theCatalogListsEveryExampleOnce() throws Exception {
        Path folder = Path.of(ExamplesTest.class.getResource("/examples").toURI());
        Set<String> files = new HashSet<>();
        try (Stream<Path> walk = Files.walk(folder)) { // every code file, as "java/sorting/BubbleSort"
            walk.filter(p -> raphael.lang.Languages.forFile(p.getFileName().toString()) != null).forEach(p -> {
                String relative = folder.relativize(p).toString().replace('\\', '/');
                files.add(relative.substring(0, relative.lastIndexOf('.')));
            });
        }
        Set<String> listed = new HashSet<>();
        Set<String> names = new HashSet<>();
        for (ExampleCatalog.Example example : ExampleCatalog.all()) {
            String where = example.language().id() + "/" + example.path();
            assertTrue(listed.add(where), where + " is listed twice");
            assertTrue(names.add(example.language().id() + ":" + example.name()), "two examples are called " + example.name());
            assertTrue(!example.title().isEmpty() && !example.blurb().isEmpty(), example.path() + " needs a title and a blurb");
        }
        assertEquals(files, listed);
        assertTrue(ExampleCatalog.groups().size() >= 10);
    }

    @Test
    void bankAccountsPrintsWhatItShould() throws Exception {
        assertEquals("> 1 earns 5.5\n> 2 earns 0.0\n> amount must be positive",
                run("BankAccounts").getOutput().trim().replace("\r\n", "\n"));
    }

    // The exceptions example shows every caught exception on the first step of its catch.
    @Test
    void exceptionsShowsEachCaughtException() throws Exception {
        Trace trace = run("Exceptions");
        assertEquals("popped 20\npopped 10\nstopped: pop on an empty stack\nfinally runs either way\nage 19\n"
                + "not an age: twenty, because For input string: \"twenty\"\nfull after 4 pushes",
                trace.getOutput().trim().replace("\r\n", "\n"));
        java.util.List<String> caught = new java.util.ArrayList<>();
        for (raphael.model.Snapshot step : trace.getSnapshots()) {
            if (step.getThrown() != null) {
                caught.add(step.getThrown().type());
            }
        }
        assertEquals(java.util.List.of("StackEmptyException", "NumberFormatException", "IllegalArgumentException",
                "IllegalStateException"), caught);
    }
}
