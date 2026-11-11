package raphael.ui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import javax.swing.BorderFactory;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import raphael.model.FrameSnapshot;
import raphael.model.HeapObject;
import raphael.model.ReferenceValue;
import raphael.model.Snapshot;
import raphael.model.Trace;
import raphael.model.Value;
import raphael.render.Style;

// The trace table: the hand trace students do on paper, made from the run. One row per step,
// with its line and method, and a column for each variable the run uses; a value that changed
// since the row before is tinted amber. The step on show is selected, and a click on a row goes
// to that step.
// OOP: inheritance (extends JPanel, and a nested AbstractTableModel that Swing's JTable asks for
// each cell); implements TraceListener (the Observer pattern).
public class TraceTablePanel extends javax.swing.JPanel implements TraceListener {

    private static final int FIXED = 3; // step, line, method
    private static final int MAX_WIDTH = 220; // a column of array cells, before it's cut off (with a tooltip)

    private final Rows rows = new Rows();
    private final JTable table = new JTable(rows);
    private boolean following; // true while the selection follows the step, so it isn't taken as a click

    public TraceTablePanel(TraceModel model) {
        super(new BorderLayout());
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setShowVerticalLines(false);
        table.setFillsViewportHeight(true);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        table.getTableHeader().setReorderingAllowed(false);
        table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object value, boolean selected, boolean focused,
                                                           int row, int column) {
                super.getTableCellRendererComponent(t, value, selected, false, row, column);
                boolean changed = column >= FIXED && rows.changed(row, column);
                setBackground(changed ? Style.CHANGE_TINT : selected ? Style.ACCENT_TINT : Style.BACKGROUND); // a change shows on the selected row too
                setHorizontalAlignment(column < 2 ? RIGHT : LEFT);
                setForeground(column < FIXED ? Style.MUTED : Style.INK);
                setBorder(BorderFactory.createEmptyBorder(0, Style.SPACE_2, 0, Style.SPACE_2));
                String text = value == null ? "" : value.toString();
                // a cut-off array's cells, in full
                setToolTipText(column >= FIXED && getFontMetrics(getFont()).stringWidth(text) + 2 * Style.SPACE_2
                        > t.getColumnModel().getColumn(column).getWidth() ? text : null);
                return this;
            }
        });
        table.getSelectionModel().addListSelectionListener(e -> {
            int row = table.getSelectedRow();
            if (!e.getValueIsAdjusting() && !following && row >= 0 && row != model.getStep()) {
                model.setStep(row);
            }
        });
        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        add(scroll, BorderLayout.CENTER);
    }

    @Override
    public void traceChanged(TraceModel model) {
        rows.load(model.getTrace());
        java.awt.FontMetrics metrics = table.getFontMetrics(table.getFont());
        for (int c = 0; c < table.getColumnCount(); c++) {
            int width = c == 0 || c == 1 ? 48 : c == 2 ? 120 : 84;
            for (int r : new int[] {table.getRowCount() / 2, table.getRowCount() - 1}) { // an array's cells: as wide as they need, to 220 px
                if (c >= FIXED && r >= 0) {
                    Object text = rows.getValueAt(r, c);
                    width = Math.max(width, Math.min(MAX_WIDTH, metrics.stringWidth(String.valueOf(text)) + 2 * Style.SPACE_2 + 4));
                }
            }
            table.getColumnModel().getColumn(c).setPreferredWidth(width);
        }
        stepChanged(model);
    }

    @Override
    public void stepChanged(TraceModel model) {
        if (!model.hasSteps() || model.getStep() >= table.getRowCount()) {
            return;
        }
        following = true;
        table.setRowSelectionInterval(model.getStep(), model.getStep());
        table.scrollRectToVisible(table.getCellRect(model.getStep(), 0, true));
        following = false;
    }

    // Asks the run for each cell, so a long run costs nothing until its rows are on screen.
    private static final class Rows extends AbstractTableModel {
        private Trace trace;
        private final List<String> variables = new ArrayList<>();

        void load(Trace trace) {
            this.trace = trace;
            variables.clear();
            if (trace != null) {
                Set<String> seen = new LinkedHashSet<>();
                for (Snapshot step : trace.getSnapshots()) {
                    if (!step.getFrames().isEmpty()) {
                        for (String name : step.getFrames().get(0).getLocals().keySet()) {
                            if (!name.equals("this")) {
                                seen.add(name);
                            }
                        }
                    }
                }
                variables.addAll(seen);
            }
            fireTableStructureChanged();
        }

        @Override
        public int getRowCount() {
            return trace == null ? 0 : trace.size();
        }

        @Override
        public int getColumnCount() {
            return FIXED + variables.size();
        }

        @Override
        public String getColumnName(int column) {
            return column == 0 ? "step" : column == 1 ? "line" : column == 2 ? "method" : variables.get(column - FIXED);
        }

        @Override
        public Object getValueAt(int row, int column) {
            Snapshot step = trace.get(row);
            if (column == 0) {
                return row + 1;
            }
            if (column == 1) {
                return step.getLine() > 0 ? step.getLine() : "";
            }
            if (step.getFrames().isEmpty()) {
                return "";
            }
            FrameSnapshot top = step.getFrames().get(0);
            return column == 2 ? top.getMethodName() + "()" : text(top.getLocals().get(variables.get(column - FIXED)), step);
        }

        // Whether this cell's value differs from the row before's, in the same method.
        boolean changed(int row, int column) {
            if (row == 0 || trace == null) {
                return false;
            }
            Snapshot now = trace.get(row);
            Snapshot before = trace.get(row - 1);
            if (now.getFrames().isEmpty() || before.getFrames().isEmpty()
                    || !now.getFrames().get(0).getMethodName().equals(before.getFrames().get(0).getMethodName())) {
                return false;
            }
            Object value = getValueAt(row, column);
            return !value.toString().isEmpty() && !Objects.equals(value, getValueAt(row - 1, column));
        }

        private static String text(Value value, Snapshot step) {
            if (value == null) {
                return "";
            }
            if (value instanceof ReferenceValue) {
                HeapObject object = step.getObject(((ReferenceValue) value).id());
                if (object instanceof raphael.model.ArrayNode && !((raphael.model.ArrayNode) object).getElementType().endsWith("[]")) {
                    String cells = object.describe(step); // "int[5] [0, 1, 4, ?, ?]": the cells, so a change shows
                    return cells.substring(cells.indexOf('[', cells.indexOf(']') + 1));
                }
                return object == null ? "→ ?" : object.shortName();
            }
            return value.display();
        }
    }
}
