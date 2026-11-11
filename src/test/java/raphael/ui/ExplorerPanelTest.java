package raphael.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class ExplorerPanelTest {

    // The rows as they show: each one's name, indented two spaces per level.
    private static List<String> rows(JTree tree) {
        List<String> rows = new ArrayList<>();
        for (int row = 0; row < tree.getRowCount(); row++) {
            rows.add("  ".repeat(tree.getPathForRow(row).getPathCount() - 1) + tree.getPathForRow(row).getLastPathComponent());
        }
        return rows;
    }

    // A folder's files as a tree under a heading with the folder's name: in each folder its folders first, then
    // its files, by name; every folder open.
    @Test
    void showsTheFilesUnderTheirFolders() throws Exception {
        List<String> shown = new ArrayList<>();
        SwingUtilities.invokeAndWait(() -> {
            ExplorerPanel explorer = new ExplorerPanel(shown::add, () -> { }, path -> false);
            explorer.show("Shapes", List.of("Main.java", "shapes/Square.java", "shapes/Circle.java", "app/util/Text.java"), "Main.java");
            assertEquals(List.of("  app", "    util", "      Text.java", "  shapes", "    Circle.java", "    Square.java",
                    "  Main.java"), rows(explorer.tree())); // the folder's name is the heading, not a row
        });
        assertEquals(List.of(), shown, "showing the file in the editor isn't a click");
    }

    // A click on a file asks for it to be shown; the file in the editor is the row chosen.
    @Test
    void aClickAsksForTheFile() throws Exception {
        List<String> shown = new ArrayList<>();
        SwingUtilities.invokeAndWait(() -> {
            ExplorerPanel explorer = new ExplorerPanel(shown::add, () -> { }, path -> false);
            explorer.show("Shapes", List.of("Main.java", "shapes/Circle.java"), "Main.java");
            assertEquals("Main.java", explorer.tree().getSelectionPath().getLastPathComponent().toString());
            explorer.tree().setSelectionRow(1); // shapes/Circle.java (row 0 is the shapes folder)
            explorer.choose("shapes/Circle.java"); // the editor follows; choosing it again isn't another click
        });
        assertEquals(List.of("shapes/Circle.java"), shown);
    }
}
