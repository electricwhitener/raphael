package raphael.ui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Font;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import raphael.model.FrameSnapshot;
import raphael.model.HeapObject;
import raphael.model.ReferenceValue;
import raphael.model.Snapshot;
import raphael.model.Value;
import raphael.render.Style;

// The variables of the running method, then the static fields, with their values.
// A reference is shown in full, e.g. head = Node{val=5, next→7}.
// A variable the last line changed (or just created) is tinted amber, like changed cells in the picture.
// OOP: inheritance (extends JPanel); implements TraceListener (Observer pattern); an anonymous
// subclass of DefaultTableCellRenderer.
public class LocalsPanel extends JPanel implements TraceListener {

    private final JLabel header = Section.header("variables");
    private final Set<Integer> changedRows = new HashSet<>();
    private final DefaultTableModel rows = new DefaultTableModel(new Object[] {"name", "value"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };

    public LocalsPanel() {
        super(new BorderLayout());
        JTable table = new JTable(rows);
        table.setShowGrid(false);
        table.setTableHeader(null); // the section header already says what this is
        table.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN); // long values get the extra room
        table.setRowHeight(22);
        table.getColumnModel().getColumn(0).setPreferredWidth(90);
        table.getColumnModel().getColumn(1).setPreferredWidth(260);
        table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean selected,
                                                           boolean focused, int row, int column) {
                super.getTableCellRendererComponent(table, value, selected, false, row, column);
                boolean changed = changedRows.contains(row);
                setBackground(changed ? Style.CHANGE_TINT : Style.BACKGROUND);
                setForeground(Style.INK);
                setFont(table.getFont().deriveFont(changed ? Font.BOLD : Font.PLAIN));
                setBorder(BorderFactory.createEmptyBorder(0, Style.SPACE_3, 0, Style.SPACE_3));
                return this;
            }
        });
        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        add(Section.wrap(header, scroll));
    }

    @Override
    public void stepChanged(TraceModel model) {
        rows.setRowCount(0);
        changedRows.clear();
        Snapshot now = model.getSnapshot();
        if (now == null || now.getFrames().isEmpty()) {
            header.setText("VARIABLES");
            return;
        }
        FrameSnapshot frame = now.getFrames().get(0);
        header.setText("VARIABLES · " + frame.getMethodName() + "()");

        // Compare with the step before, but only if it was in the same method call.
        Snapshot before = model.getPreviousSnapshot();
        FrameSnapshot beforeFrame = before == null || before.getFrames().isEmpty() ? null : before.getFrames().get(0);
        boolean sameCall = beforeFrame != null && before.getFrames().size() == now.getFrames().size()
                && beforeFrame.getMethodName().equals(frame.getMethodName());
        for (Map.Entry<String, Value> local : frame.getLocals().entrySet()) {
            boolean changed = sameCall && !local.getValue().equals(beforeFrame.getLocals().get(local.getKey()));
            addRow(local.getKey(), describe(local.getValue(), now), changed);
        }
        for (Map.Entry<String, Value> field : now.getStatics().entrySet()) {
            boolean changed = before != null && !field.getValue().equals(before.getStatics().get(field.getKey()));
            String name = field.getKey().substring(field.getKey().indexOf('.') + 1); // without the class name
            addRow("static " + name, describe(field.getValue(), now), changed);
        }
    }

    private void addRow(String name, String value, boolean changed) {
        if (changed) {
            changedRows.add(rows.getRowCount());
        }
        rows.addRow(new Object[] {name, value});
    }

    private static String describe(Value value, Snapshot snapshot) {
        if (value instanceof ReferenceValue) {
            HeapObject object = snapshot.getObject(((ReferenceValue) value).id());
            if (object != null) {
                return object.describe(snapshot);
            }
        }
        return value.display();
    }
}
