package raphael;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import raphael.compile.CompileResult;
import raphael.compile.SourceCompiler;
import raphael.model.EndReason;
import raphael.model.Snapshot;
import raphael.model.Trace;
import raphael.render.Scene;
import raphael.render.SceneBuilder;
import raphael.render.ScenePainter;
import raphael.trace.JdiTracer;

// Every program in src/test/resources/edge/ is a tricky case: threads, System.exit, unicode,
// cycles, running out of memory or input… Each says at its top what must happen:
//   // expect: FINISHED | EXCEPTION | STEP_LIMIT | TIMEOUT | COMPILE_ERROR
//   // input: text typed into System.in
//   // output: text the output must contain (one line each)
//   // no-output: text the output must not contain
//   // output-length-at-least: a number of characters
//   // message: text the end message must contain
//   // error-line: the line a compile error must be on
// Whatever the program does, Raphael must also be able to detect, lay out and draw every step.
class EdgeCaseTest {

    static Stream<String> programs() throws URISyntaxException, java.io.IOException {
        Path folder = Path.of(EdgeCaseTest.class.getResource("/edge").toURI());
        try (Stream<Path> files = Files.list(folder)) {
            return files.map(p -> p.getFileName().toString()).filter(n -> n.endsWith(".java")).sorted().toList().stream();
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("programs")
    void runsAsExpected(String file) throws Exception {
        String source = new String(EdgeCaseTest.class.getResourceAsStream("/edge/" + file).readAllBytes(),
                StandardCharsets.UTF_8);
        String expect = header(source, "expect").get(0);

        CompileResult program = new SourceCompiler().compile(source);
        if (expect.equals("COMPILE_ERROR")) {
            assertFalse(program.success(), "should not compile");
            for (String line : header(source, "error-line")) {
                assertTrue(program.errors().stream().anyMatch(e -> e.line() == Long.parseLong(line)),
                        () -> "no error on line " + line + ": " + program.errors());
            }
            return;
        }
        assertTrue(program.success(), () -> "didn't compile: " + program.errors());

        String input = String.join("\n", header(source, "input"));
        Trace trace = assertTimeoutPreemptively(Duration.ofSeconds(90), () -> new JdiTracer().trace(program, input));
        assertEquals(EndReason.valueOf(expect), trace.getEndReason(), trace.getEndMessage());

        String output = trace.getOutput().replace("\r\n", "\n");
        for (String text : header(source, "output")) {
            assertTrue(output.contains(text), () -> "output lacks \"" + text + "\": " + shorten(output));
        }
        for (String text : header(source, "no-output")) {
            assertFalse(output.contains(text), () -> "output has \"" + text + "\"");
        }
        for (String length : header(source, "output-length-at-least")) {
            assertTrue(output.length() >= Integer.parseInt(length), "output is only " + output.length() + " characters");
        }
        for (String text : header(source, "message")) {
            assertTrue(trace.getEndMessage().contains(text), () -> "end message: " + trace.getEndMessage());
        }

        for (Snapshot step : trace.getSnapshots()) {
            assertTrue(step.getLine() > 0, "a step without a line");
        }
        List<Scene> scenes = new SceneBuilder(source).build(trace);
        assertEquals(trace.size(), scenes.size());
        if (!scenes.isEmpty()) { // draw the last step, as the window would
            BufferedImage image = new BufferedImage(800, 600, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = image.createGraphics();
            new ScenePainter().paint(g, scenes.get(scenes.size() - 1));
            g.dispose();
        }
    }

    // The values of every "// key: value" line at the top of the program.
    private static List<String> header(String source, String key) {
        List<String> values = new ArrayList<>();
        for (String line : source.split("\r?\n")) {
            if (!line.startsWith("//")) {
                break;
            }
            String prefix = "// " + key + ": ";
            if (line.startsWith(prefix)) {
                values.add(line.substring(prefix.length()).trim());
            }
        }
        return values;
    }

    private static String shorten(String text) {
        return text.length() > 300 ? text.substring(0, 300) + "…" : text;
    }
}
