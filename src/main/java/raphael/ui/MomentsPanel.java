package raphael.ui;

import java.util.List;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import raphael.explain.Moment;
import raphael.render.Style;

// The run's key moments as a list: "step 9 · Base case", with the sentence under it. The moment
// of the step on show is selected; clicking one goes to its step.
// OOP: inheritance (extends JPanel); implements TraceListener (the Observer pattern); a nested
// class draws each row.
public class MomentsPanel extends JPanel implements TraceListener {

    private final TraceModel model;
    private final DefaultListModel<Moment> rows = new DefaultListModel<>();
    // The full sentence shows on hover (a list asks itself for its tooltip, not its rows).
    private final JList<Moment> list = new JList<>(rows) {
        @Override
        public String getToolTipText(MouseEvent e) {
            int index = locationToIndex(e.getPoint());
            return index < 0 ? null : "<html><p width='360'>" + rows.get(index).text() + "</p></html>";
        }
    };
    private final JLabel empty = new JLabel("No key moments in this run yet.");

    public MomentsPanel(TraceModel model) {
        super(new BorderLayout());
        this.model = model;
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer(new Row());
        list.setFixedCellHeight(ROW_H); // a title and two lines of sentence; more ends in … (the tooltip has it all)
        javax.swing.ToolTipManager.sharedInstance().registerComponent(list);
        list.getInputMap().put(javax.swing.KeyStroke.getKeyStroke("ENTER"), "go");
        list.getActionMap().put("go", new javax.swing.AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                if (list.getSelectedIndex() >= 0) {
                    model.setStep(rows.get(list.getSelectedIndex()).step());
                }
            }
        });
        list.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int index = list.locationToIndex(e.getPoint());
                if (index >= 0) {
                    model.setStep(rows.get(index).step());
                }
            }
        });
        JScrollPane scroll = new JScrollPane(list);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        empty.setFont(Style.sans(false, 12f));
        empty.setForeground(Style.MUTED);
        empty.setBorder(BorderFactory.createEmptyBorder(Style.SPACE_2, Style.SPACE_3, 0, 0));
        add(empty, BorderLayout.NORTH);
        add(scroll, BorderLayout.CENTER);
    }

    @Override
    public void traceChanged(TraceModel model) {
        rows.clear();
        for (Moment moment : model.getMoments()) {
            rows.addElement(moment);
        }
        empty.setVisible(rows.isEmpty());
        stepChanged(model);
    }

    @Override
    public void momentsChanged(TraceModel model) {
        traceChanged(model);
    }

    @Override
    public void stepChanged(TraceModel model) {
        list.clearSelection();
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).step() == model.getStep()) {
                list.setSelectedIndex(i);
                list.ensureIndexIsVisible(i);
                return;
            }
        }
    }

    private static final int ROW_H = 2 * Style.SPACE_1 + 16 + 2 * Style.NOTE_LINE_H; // 4 + 16 + 36 + 4
    private static final int TWO_LINES_FROM = 360; // narrower than this, the sentence gets one line

    // One moment: its step and title, and its sentence in muted text, wrapped to two lines.
    private static final class Row implements ListCellRenderer<Moment> {
        private final JPanel panel = new JPanel(new BorderLayout());
        private final JLabel title = new JLabel();
        private final JLabel text = new JLabel();

        Row() {
            panel.setBorder(BorderFactory.createEmptyBorder(Style.SPACE_1, Style.SPACE_3, Style.SPACE_1, Style.SPACE_3));
            title.setFont(Style.sans(true, 12f));
            text.setFont(Style.sans(false, 12f));
            panel.add(title, BorderLayout.NORTH);
            panel.add(text, BorderLayout.CENTER);
            panel.setPreferredSize(new java.awt.Dimension(0, ROW_H)); // as wide as the list: the text wraps to it
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends Moment> list, Moment moment, int index,
                                                      boolean selected, boolean focused) {
            title.setText("step " + (moment.step() + 1) + " · " + moment.title());
            title.setForeground(moment.pauses() ? Style.ACCENT : Style.INK);
            int width = list.getWidth() - 3 - 2 * Style.SPACE_3;
            List<String> lines = wrap(moment.text(), text.getFontMetrics(text.getFont()), width,
                    list.getWidth() < TWO_LINES_FROM ? 1 : 2);
            text.setText(lines.size() == 1 ? lines.get(0)
                    : "<html>" + escape(lines.get(0)) + "<br>" + escape(lines.get(1)) + "</html>");
            text.setVerticalAlignment(JLabel.TOP);
            text.setForeground(Style.MUTED);
            panel.setToolTipText(moment.text());
            panel.setBackground(selected ? Style.ACCENT_TINT : Style.BACKGROUND);
            panel.setBorder(BorderFactory.createCompoundBorder( // a bar at the left: blue where play pauses
                    BorderFactory.createMatteBorder(0, 3, 0, 0, moment.pauses() ? Style.ACCENT : Style.HAIRLINE),
                    BorderFactory.createEmptyBorder(Style.SPACE_1, Style.SPACE_3, Style.SPACE_1, Style.SPACE_3)));
            return panel;
        }

        // The words of a sentence in at most `most` lines of this width; the last ends in … if
        // some didn't fit.
        static List<String> wrap(String sentence, java.awt.FontMetrics metrics, int width, int most) {
            List<String> lines = new java.util.ArrayList<>();
            StringBuilder line = new StringBuilder();
            String[] words = sentence.split(" ");
            for (int w = 0; w < words.length; w++) {
                String next = line.length() == 0 ? words[w] : line + " " + words[w];
                if (width > 0 && metrics.stringWidth(next) > width && line.length() > 0) {
                    lines.add(line.toString());
                    line = new StringBuilder(words[w]);
                    if (lines.size() == most) {
                        String last = lines.remove(most - 1);
                        while (!last.isEmpty() && metrics.stringWidth(last + " …") > width) {
                            last = last.substring(0, last.length() - 1);
                        }
                        lines.add(last.strip() + " …");
                        return lines;
                    }
                } else {
                    line = new StringBuilder(next);
                }
            }
            lines.add(line.toString());
            return lines;
        }

        private static String escape(String text) {
            return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        }
    }
}
