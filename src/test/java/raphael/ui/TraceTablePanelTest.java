package raphael.ui;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import raphael.model.ArrayNode;
import raphael.model.EndReason;
import raphael.model.FrameSnapshot;
import raphael.model.PrimitiveValue;
import raphael.model.ReferenceValue;
import raphael.model.Snapshot;
import raphael.model.Trace;
import raphael.model.Value;

class TraceTablePanelTest {

    // An array's column shows its cells. Java writes the type with brackets (int[3]), C++ without
    // (vector<Shape*>(2)); the table used to fail on C++'s and stop the whole run from showing.
    @Test
    void anArrayColumnShowsTheCellsInEveryLanguage() throws Exception {
        String[] cells = new String[2];
        SwingUtilities.invokeAndWait(() -> {
            cells[0] = cellsOf(new ArrayNode(1, "int[]", "int", List.of(new PrimitiveValue("5"), new PrimitiveValue("2")), 2));
            cells[1] = cellsOf(new ArrayNode(1, "vector<Shape*>", "Shape*", List.of(new PrimitiveValue("1.0")), 1));
        });
        assertEquals("[5, 2]", cells[0]);
        assertEquals("[1.0]", cells[1]);
    }

    private static String cellsOf(ArrayNode array) {
        Map<String, Value> locals = Map.of("a", new ReferenceValue(1));
        Snapshot step = new Snapshot(List.of(new FrameSnapshot("Main", "main", 3, locals)), Map.of(1L, array), Map.of(), 0, null);
        TraceModel model = new TraceModel();
        TraceTablePanel panel = new TraceTablePanel(model);
        model.addListener(panel);
        model.load(new Trace(List.of(step), "", EndReason.FINISHED, "done"), List.of(new raphael.render.Scene()));
        JTable table = find(panel);
        return String.valueOf(table.getValueAt(0, table.getColumnCount() - 1));
    }

    private static JTable find(java.awt.Container in) {
        for (java.awt.Component part : in.getComponents()) {
            if (part instanceof JTable table) {
                return table;
            }
            if (part instanceof java.awt.Container container && find(container) != null) {
                return find(container);
            }
        }
        return null;
    }
}
