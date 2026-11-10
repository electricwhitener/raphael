package raphael.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.awt.GraphicsEnvironment;
import java.util.List;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class CommandPaletteTest {

    // The letters typed must come in order; starts of words and letters side by side rank higher.
    @Test
    void lettersInOrderMatch() {
        assertTrue(CommandPalette.score("File: Open Folder…", "opfo") > 0);
        assertEquals(-1, CommandPalette.score("File: Open Folder…", "folp"), "out of order");
        assertTrue(CommandPalette.score("File: Open Folder…", "folder") > CommandPalette.score("Edit: Find…", "fd"));
        assertEquals(0, CommandPalette.score("anything", " "), "nothing typed: everything, unranked");
    }

    @Test
    void theListNarrowsAsYouType() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "needs a screen");
        SwingUtilities.invokeAndWait(() -> {
            JFrame owner = new JFrame();
            owner.setBounds(0, 0, 800, 600);
            List<CommandPalette.Entry> entries = List.of(
                    new CommandPalette.Entry("Edit: Find…", "Ctrl+F", () -> { }),
                    new CommandPalette.Entry("File: Open Folder…", "Ctrl+Shift+O", () -> { }),
                    new CommandPalette.Entry("File: Open…", "Ctrl+O", () -> { }));
            CommandPalette palette = CommandPalette.forTests(owner, entries);
            assertEquals(3, palette.shownFor("").size());
            assertEquals(List.of("File: Open Folder…"), palette.shownFor("opfo"));
            assertEquals("File: Open…", palette.shownFor("open").get(0), "the shorter, closer match first");
            palette.dispose();
            owner.dispose();
        });
    }
}
