package raphael.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import raphael.trace.RunLimits;

class WorkbenchTest {

    // A window that only counts how often it was told about a change.
    private static final class FakeHost implements Workbench.Host {
        int changes;

        @Override
        public void stateChanged(Workbench workbench) {
            changes++;
        }

        @Override
        public File chooseFile(boolean forSaving, FileKind kind, String suggested) {
            return null;
        }

        @Override
        public File chooseFolder() {
            return null;
        }

        @Override
        public void remember(File file) {
        }

        @Override
        public void openDropped(File file) {
        }

        @Override
        public void loadExample(String name) {
        }

        @Override
        public void showExamples() {
        }

        @Override
        public void changeTextSize(int change) {
        }

        @Override
        public void caretMoved(Workbench workbench) {
        }

        @Override
        public RunLimits limits() {
            return RunLimits.DEFAULTS;
        }
    }

    private static final String COUNT_TO = """
            public class Count {
                public static void main(String[] args) {
                    int total = 0;
                    for (int i = 1; i <= %d; i++) {
                        total += i;
                    }
                    System.out.println(total);
                }
            }
            """;

    // Two programs record at the same time, and each run lands in the program that started it.
    @Test
    void twoProgramsRecordAtOnceIntoTheirOwnWorkbench() throws Exception {
        FakeHost host = new FakeHost();
        Workbench[] programs = new Workbench[2];
        SwingUtilities.invokeAndWait(() -> {
            programs[0] = new Workbench(host);
            programs[1] = new Workbench(host);
            programs[0].showCode(COUNT_TO.formatted(3), null, "three");
            programs[1].showCode(COUNT_TO.formatted(6), null, "six");
            programs[0].run();
            programs[1].run();
            assertTrue(programs[0].isRunning() && programs[1].isRunning());
        });
        waitUntilDone(programs);
        SwingUtilities.invokeAndWait(() -> {
            for (Workbench program : programs) {
                assertTrue(program.getModel().hasSteps(), program.statusText());
            }
            assertEquals("6\n", programs[0].getModel().getTrace().getOutput().replace("\r\n", "\n"));
            assertEquals("21\n", programs[1].getModel().getTrace().getOutput().replace("\r\n", "\n"));
            assertTrue(programs[0].hasRecording(), "the run can be saved as a recording");
            assertTrue(programs[0].statusText().startsWith("[ok]"), programs[0].statusText());
        });
        assertTrue(host.changes > 0, "the window was told when the runs started and ended");
    }

    // After a run, the editor marks the line about to run. (In 1.1.0 a program of one file had a
    // file name in its frames that the editor didn't know, so no line was marked.)
    @Test
    void theEditorMarksTheRunningLine() throws Exception {
        FakeHost host = new FakeHost();
        Workbench[] program = new Workbench[1];
        SwingUtilities.invokeAndWait(() -> {
            program[0] = new Workbench(host);
            program[0].showCode(COUNT_TO.formatted(2), null, "two");
            program[0].run();
        });
        waitUntilDone(program);
        SwingUtilities.invokeAndWait(() -> {
            TraceModel model = program[0].getModel();
            model.setStep(1);
            assertEquals(model.getSnapshot().getLine(), program[0].getCode().markedLine());
        });
    }

    // Code that doesn't compile shows its errors in its own program, and nothing is recorded.
    @Test
    void aCompileErrorStaysInItsProgram() throws Exception {
        FakeHost host = new FakeHost();
        Workbench[] program = new Workbench[1];
        SwingUtilities.invokeAndWait(() -> {
            program[0] = new Workbench(host);
            program[0].showCode("public class Broken { void f() { int x = } }", null, "broken");
            program[0].run();
        });
        waitUntilDone(program);
        SwingUtilities.invokeAndWait(() -> {
            assertFalse(program[0].getModel().hasSteps());
            assertTrue(program[0].statusText().startsWith("[error]"), program[0].statusText());
            assertFalse(program[0].wantsLegend(), "an error outranks the picture's key");
        });
    }

    private static void waitUntilDone(Workbench[] programs) throws Exception {
        long deadline = System.currentTimeMillis() + 60_000;
        AtomicBoolean running = new AtomicBoolean(true);
        while (running.get() && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
            SwingUtilities.invokeAndWait(() -> {
                boolean any = false;
                for (Workbench program : programs) {
                    any |= program.isRunning();
                }
                running.set(any);
            });
        }
        assertFalse(running.get(), "the runs finished within a minute");
    }

    // A saved file another program changed is read again, still saved; one only touched (the same
    // text, a new time) stays as it is.
    @Test
    void aFileChangedOnDiskIsReadAgain(@org.junit.jupiter.api.io.TempDir java.nio.file.Path folder) throws Exception {
        File file = folder.resolve("Count.java").toFile();
        String three = COUNT_TO.formatted(3);
        java.nio.file.Files.writeString(file.toPath(), three);
        Workbench[] program = new Workbench[1];
        SwingUtilities.invokeAndWait(() -> {
            program[0] = new Workbench(new FakeHost());
            program[0].showCode(three, file, file.getName());
        });
        java.nio.file.Files.writeString(file.toPath(), three);
        assertTrue(file.setLastModified(file.lastModified() + 5000)); // touched, not changed
        SwingUtilities.invokeAndWait(() -> program[0].checkDisk());
        assertEquals(three, program[0].getCode().getCode());

        String six = COUNT_TO.formatted(6);
        java.nio.file.Files.writeString(file.toPath(), six);
        assertTrue(file.setLastModified(file.lastModified() + 10000)); // the file system's clock may be coarse
        SwingUtilities.invokeAndWait(() -> program[0].checkDisk());
        assertEquals(six, program[0].getCode().getCode());
        assertFalse(program[0].isDirty(), "the reloaded text is what's saved");
    }
}
