package raphael.ui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Font;
import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import raphael.model.FrameSnapshot;
import raphael.model.Snapshot;
import raphael.render.Style;

// The methods running right now, newest on top:  insert()  :16  above  main()  :24.
// The newest one (the method that's running) is bold and blue.
// OOP: inheritance (extends JPanel); implements TraceListener (Observer pattern); an anonymous
// subclass of DefaultListCellRenderer.
public class CallStackPanel extends JPanel implements TraceListener {

    private static final String LEFT_MARK = "↑ "; // marks a method a caught exception left
    private final DefaultListModel<String> frames = new DefaultListModel<>();
    private final JList<String> list = new JList<>(frames);

    public CallStackPanel() {
        super(new BorderLayout());
        list.setFocusable(false);
        list.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                          boolean selected, boolean focused) {
                super.getListCellRendererComponent(list, value, index, false, false);
                setBorder(BorderFactory.createEmptyBorder(Style.SPACE_1, Style.SPACE_3, Style.SPACE_1, Style.SPACE_3));
                boolean left = String.valueOf(value).startsWith(LEFT_MARK);
                boolean running = !left && (index == 0 || String.valueOf(frames.get(index - 1)).startsWith(LEFT_MARK));
                setFont(list.getFont().deriveFont(running ? Font.BOLD : Font.PLAIN));
                setForeground(left ? Style.MUTED : running ? Style.ACCENT : Style.INK_SOFT);
                return this;
            }
        });
        JScrollPane scroll = new JScrollPane(list);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        add(Section.wrap(Section.header("call stack"), scroll));
    }

    @Override
    public void stepChanged(TraceModel model) {
        frames.clear();
        Snapshot snapshot = model.getSnapshot();
        if (snapshot == null) {
            return;
        }
        for (FrameSnapshot frame : snapshot.getFrames()) {
            frames.addElement(String.format("%-14s :%d", frame.getMethodName() + "()", frame.getLine()));
        }
        // The methods a caught exception just left, each stopped in the middle: greyed, above the
        // method that caught it, where they were on the stack.
        if (snapshot.getThrown() != null) {
            java.util.List<String> left = snapshot.getThrown().leftMethods(); // from the one that threw, outwards
            for (int i = 0; i < left.size(); i++) {
                frames.add(i, LEFT_MARK + left.get(i) + "()  (left)");
            }
        }
    }
}
