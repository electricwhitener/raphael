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
                setBorder(BorderFactory.createEmptyBorder(3, 8, 3, 8));
                setFont(list.getFont().deriveFont(index == 0 ? Font.BOLD : Font.PLAIN));
                setForeground(index == 0 ? Style.ACCENT : Style.INK_SOFT);
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
    }
}
