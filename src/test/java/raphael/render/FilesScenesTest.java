package raphael.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import raphael.compile.CompileResult;
import raphael.compile.SourceCompiler;
import raphael.explain.Reads;
import raphael.io.ExampleCatalog;
import raphael.model.Trace;
import raphael.trace.JdiTracer;

class FilesScenesTest {

    private static final String READER = String.join("\n",
            "import java.util.Scanner;",
            "public class Ages {",
            "    public static void main(String[] args) {",
            "        Scanner sc = new Scanner(System.in);",
            "        int age = sc.nextInt();",
            "        String rest = sc.nextLine();",
            "        String name = sc.nextLine();",
            "        System.out.println(name + \" is \" + age);",
            "    }",
            "}");

    // The classic surprise: nextInt() takes "20" and leaves the line's end, so the first
    // nextLine() reads nothing but that ↵, and only the second one reads the name.
    @Test
    void theInputIsReadAsAScannerReadsIt() throws Exception {
        CompileResult program = new SourceCompiler().compile(READER);
        String input = "20\nAsha\n";
        Trace trace = new JdiTracer().trace(program, input);
        Reads reads = new Reads(trace, program.facts(), input);
        int age = -1;
        int rest = -1;
        int name = -1;
        for (int s = 0; s < trace.size(); s++) {
            for (Reads.Spot spot : reads.justRead(s)) {
                String text = input.substring(spot.from(), spot.to());
                if ("age".equals(spot.into())) {
                    age = s;
                    assertEquals("20", text);
                } else if ("rest".equals(spot.into())) {
                    rest = s;
                    assertEquals("\n", text, "the rest of the first line is only its end");
                } else if ("name".equals(spot.into())) {
                    name = s;
                    assertEquals("Asha\n", text);
                }
            }
        }
        assertTrue(age > 0 && rest > age && name > rest, age + " " + rest + " " + name);
        assertEquals(input.length(), reads.readUpTo(trace.size() - 1, Reads.INPUT));
        Scene scene = new FilesScenes(trace, program.facts(), input).sceneAt(rest);
        assertEquals("← read into rest", scene.getNode("input:into").text());
        assertEquals(SceneNode.Look.DIM, scene.getNode("input:0").look(), "20 was read a step before");
    }

    // WriteAndRead: reading scores.txt back, the line that just went into `line` says so, and
    // the lines before it are faded.
    @Test
    void aFileIsReadLineByLine() throws Exception {
        CompileResult program = new SourceCompiler().compile(ExampleCatalog.source(ExampleCatalog.find("WriteAndRead")));
        Trace trace = new JdiTracer().trace(program);
        Reads reads = new Reads(trace, program.facts(), "");
        int second = -1;
        int count = 0;
        for (int s = 0; s < trace.size(); s++) {
            for (Reads.Spot spot : reads.justRead(s)) {
                assertEquals("scores.txt", spot.source());
                assertEquals("line", spot.into());
                if (++count == 2) {
                    second = s;
                }
            }
        }
        assertEquals(3, count, "three lines, each read once");
        Scene scene = new FilesScenes(trace, program.facts(), "").sceneAt(second);
        assertEquals(SceneNode.Look.DIM, scene.getNode("file:scores.txt.line:0").look());
        assertTrue(scene.getNode("file:scores.txt.line:1").text().contains("Ben,67"));
        assertNotNull(scene.getNode("file:scores.txt.into"));
    }

    // MissingFile: the file it looked for is a ghost once it wasn't found, until the catch makes it.
    @Test
    void aMissingFileIsAGhost() throws Exception {
        CompileResult program = new SourceCompiler().compile(ExampleCatalog.source(ExampleCatalog.find("MissingFile")));
        Trace trace = new JdiTracer().trace(program);
        int thrown = 0;
        while (trace.get(thrown).getThrown() == null) {
            thrown++;
        }
        FilesScenes view = new FilesScenes(trace, program.facts(), "");
        SceneNode ghost = view.sceneAt(thrown).getNode("file:settings.txt");
        assertEquals("settings.txt  (not there)", ghost.caption());
        assertEquals(SceneNode.Look.DIM, ghost.look());
        assertEquals("settings.txt", view.sceneAt(trace.size() - 1).getNode("file:settings.txt").caption(), "then the catch makes it");
    }
}
