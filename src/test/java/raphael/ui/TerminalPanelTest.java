package raphael.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import raphael.model.EndReason;
import raphael.model.FrameSnapshot;
import raphael.model.Snapshot;
import raphael.model.Trace;
import raphael.model.TypedLine;
import raphael.trace.LiveInput;

// The terminal: live output, typing after it (and only after it), lines sent on Enter, and the
// run played back with each typed line where it was typed.
class TerminalPanelTest {

    private static void onSwing(Runnable work) throws Exception {
        SwingUtilities.invokeAndWait(work);
    }

    @Test
    void typedLinesGoToTheProgramAndShowAfterThePrompt() throws Exception {
        LiveInput live = new LiveInput("", (file, line) -> true);
        AtomicReference<String> printed = new AtomicReference<>("name? ");
        TerminalPanel[] terminal = new TerminalPanel[1];
        onSwing(() -> {
            terminal[0] = new TerminalPanel();
            terminal[0].startRun(live, printed::get);
            terminal[0].refresh();
            assertEquals("name? ", terminal[0].text());
            assertTrue(terminal[0].accepting());
            terminal[0].typeForTest("Ada");
            assertEquals("name? Ada", terminal[0].text(), "typed at the end");
            assertTrue(live.typed().isEmpty(), "not sent before Enter");
            terminal[0].typeForTest("\n");
        });
        onSwing(() -> { }); // the line is sent after the key's own work
        assertEquals(List.of("Ada\n"), live.typed().stream().map(TypedLine::text).toList());
        onSwing(() -> {
            printed.set("name? hi Ada\n");
            terminal[0].refresh();
            assertEquals("name? Ada\nhi Ada\n", terminal[0].text(), "the output goes on after the typed line");
        });
    }

    @Test
    void thePrintedTextCantBeEdited() throws Exception {
        LiveInput live = new LiveInput("", (file, line) -> true);
        onSwing(() -> {
            TerminalPanel terminal = new TerminalPanel();
            terminal.startRun(live, () -> "prompt> ");
            terminal.refresh();
            javax.swing.text.JTextComponent screen = screenOf(terminal);
            try {
                screen.getDocument().remove(0, 3); // as the user's Backspace would
                screen.getDocument().insertString(2, "x", null);
            } catch (javax.swing.text.BadLocationException e) {
                throw new AssertionError(e);
            }
            assertEquals("prompt> ", terminal.text());
        });
    }

    @Test
    void ctrlDEndsTheInput() throws Exception {
        LiveInput live = new LiveInput("", (file, line) -> true);
        onSwing(() -> {
            TerminalPanel terminal = new TerminalPanel();
            terminal.startRun(live, () -> "");
            javax.swing.text.JTextComponent screen = screenOf(terminal);
            javax.swing.Action end = screen.getActionMap().get(screen.getInputMap().get(javax.swing.KeyStroke.getKeyStroke("control D")));
            assertTrue(end.isEnabled());
            end.actionPerformed(null);
            assertTrue(live.isEnded());
            assertFalse(terminal.accepting());
            assertFalse(end.isEnabled(), "now Ctrl+D goes on to the window (the theme)");
        });
    }

    @Test
    void aProgramThatReadsNothingCantBeTypedTo() throws Exception {
        onSwing(() -> {
            TerminalPanel terminal = new TerminalPanel();
            terminal.startRun(null, () -> "hello\n");
            terminal.refresh();
            assertFalse(terminal.accepting());
            terminal.typeForTest("x"); // refused
            assertEquals("hello\n", terminal.text());
        });
    }

    // Played back: each typed line shows from the step it was typed at, where it was typed.
    @Test
    void playbackShowsTypedLinesFromTheirStep() throws Exception {
        List<Snapshot> steps = new java.util.ArrayList<>();
        List<raphael.render.Scene> scenes = new java.util.ArrayList<>();
        int[] printedBy = {0, 6, 6, 13}; // "name? " by step 1; "hi Ada\n" by step 3
        for (int length : printedBy) {
            steps.add(new Snapshot(List.of(new FrameSnapshot("Main", "main", 1, java.util.Map.of())), java.util.Map.of(),
                    java.util.Map.of(), length, null));
            scenes.add(new raphael.render.Scene());
        }
        Trace trace = new Trace(steps, "name? hi Ada\n", EndReason.FINISHED, "Program finished.",
                List.of(new TypedLine(2, 6, "Ada\n")));
        onSwing(() -> {
            TerminalPanel terminal = new TerminalPanel();
            TraceModel model = new TraceModel();
            model.addListener(terminal);
            model.load(trace, scenes);
            model.setStep(1);
            assertEquals("name? ", terminal.text(), "waiting: not typed yet");
            model.setStep(2);
            assertEquals("name? Ada\n", terminal.text());
            model.setStep(3);
            assertEquals("name? Ada\nhi Ada\n— Program finished.", terminal.text());
        });
    }

    private static javax.swing.text.JTextComponent screenOf(java.awt.Container in) {
        for (java.awt.Component child : in.getComponents()) {
            if (child instanceof javax.swing.JTextPane) {
                return (javax.swing.text.JTextComponent) child;
            }
            if (child instanceof java.awt.Container) {
                javax.swing.text.JTextComponent found = screenOf((java.awt.Container) child);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }
}
