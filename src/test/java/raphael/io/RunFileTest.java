package raphael.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import raphael.compile.SourceCompiler;
import raphael.model.HeapObject;
import raphael.model.NullValue;
import raphael.model.Snapshot;
import raphael.model.Trace;
import raphael.model.Value;
import raphael.render.SceneBuilder;
import raphael.trace.JdiTracer;

class RunFileTest {

    @TempDir
    Path folder;

    private static String source;
    private static Trace trace;

    @BeforeAll
    static void record() throws Exception {
        source = ExampleCatalog.source(ExampleCatalog.find("ReverseList"));
        trace = new JdiTracer().trace(new SourceCompiler().compile(source), "");
    }

    @Test
    void aSavedRunComesBackTheSame() throws Exception {
        Path file = folder.resolve("reverse.raphael");
        RunFile.write(file, new RunFile.Saved(source, "some input", trace));
        RunFile.Saved read = RunFile.read(file);
        assertEquals(source, read.source());
        assertEquals("some input", read.input());
        Trace back = read.trace();
        assertEquals(trace.size(), back.size());
        assertEquals(trace.getOutput(), back.getOutput());
        assertEquals(trace.getEndReason(), back.getEndReason());
        for (int i = 0; i < trace.size(); i++) {
            Snapshot before = trace.get(i);
            Snapshot after = back.get(i);
            assertEquals(before.getLine(), after.getLine(), "step " + i);
            assertEquals(before.getHeap().keySet(), after.getHeap().keySet(), "step " + i);
            for (Long id : before.getHeap().keySet()) {
                assertEquals(before.getHeap().get(id).describe(before), after.getHeap().get(id).describe(after), "step " + i);
            }
        }
        // The pictures can be built again from the file alone.
        assertEquals(trace.size(), new SceneBuilder(read.source()).build(back).size());
    }

    // Objects shared by several steps (Raphael remembers unchanged ones) are written once and
    // stay shared; null stays the one NullValue.
    @Test
    void sharedObjectsStayShared() throws Exception {
        Path file = folder.resolve("shared.raphael");
        RunFile.write(file, new RunFile.Saved(source, "", trace));
        Trace back = RunFile.read(file).trace();
        int shared = 0;
        for (int i = 1; i < trace.size(); i++) {
            for (Long id : trace.get(i).getHeap().keySet()) {
                HeapObject now = trace.get(i).getHeap().get(id);
                if (now == trace.get(i - 1).getHeap().get(id)) {
                    shared++;
                    assertSame(back.get(i).getHeap().get(id), back.get(i - 1).getHeap().get(id));
                }
            }
        }
        assertTrue(shared > 0, "the run shares objects between steps");
        Value nothing = back.get(back.size() - 1).getFrames().get(0).getLocals().values().stream()
                .filter(v -> v.display().equals("null")).findFirst().orElse(NullValue.INSTANCE);
        assertSame(NullValue.INSTANCE, nothing);
    }

    @Test
    void anotherKindOfFileIsRefused() throws Exception {
        Path file = folder.resolve("notes.raphael");
        Files.writeString(file, "just some text, not a run");
        RunFileException e = assertThrows(RunFileException.class, () -> RunFile.read(file));
        assertEquals("notes.raphael isn't a run saved by Raphael.", e.getMessage());
    }

    @Test
    void aFileCutShortIsCalledDamaged() throws Exception {
        Path file = folder.resolve("cut.raphael");
        RunFile.write(file, new RunFile.Saved(source, "", trace));
        byte[] bytes = Files.readAllBytes(file);
        Files.write(file, Arrays.copyOf(bytes, bytes.length / 2));
        RunFileException e = assertThrows(RunFileException.class, () -> RunFile.read(file));
        assertTrue(e.getMessage().contains("damaged"), e.getMessage());
    }

    @Test
    void aNewerFormatAsksForANewerRaphael() throws Exception {
        Path file = folder.resolve("future.raphael");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream header = new DataOutputStream(bytes);
        header.writeInt(0x52415048);
        header.writeInt(99);
        Files.write(file, bytes.toByteArray());
        RunFileException e = assertThrows(RunFileException.class, () -> RunFile.read(file));
        assertTrue(e.getMessage().contains("newer Raphael"), e.getMessage());
    }

    // A file made to look like a run but holding another kind of object is never turned back
    // into that object: only Raphael's own model classes may be read.
    @Test
    void onlyRaphaelsOwnClassesAreRead() throws Exception {
        Path file = folder.resolve("sneaky.raphael");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream header = new DataOutputStream(bytes);
        header.writeInt(0x52415048);
        header.writeInt(1);
        header.flush();
        try (ObjectOutputStream objects = new ObjectOutputStream(new GZIPOutputStream(bytes))) {
            objects.writeObject(new java.io.File("anything"));
        }
        Files.write(file, bytes.toByteArray());
        assertThrows(RunFileException.class, () -> RunFile.read(file));
    }
}
