package raphael.ui;

import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;
import javax.swing.ButtonGroup;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import raphael.render.Style;

// A row of equal cells where one is chosen (a segmented control), like the switch between light and
// dark in Settings: the chosen cell is filled blue with bold text, the others sit on the bar's
// shade. The colours are read while painting, so it follows dark mode by itself. Tab reaches it,
// and the arrow keys move between cells (Swing's toggle buttons in a group).
// OOP: inheritance (extends JPanel); composition (a group of toggle buttons); anonymous
// subclasses override how each cell is coloured; the choice is passed out through an IntConsumer.
public class Segmented extends JPanel {

    private final List<JToggleButton> cells = new ArrayList<>();

    public Segmented(List<String> names, IntConsumer chosen) {
        super(new GridLayout(1, 0, 0, 0));
        setOpaque(false);
        ButtonGroup group = new ButtonGroup();
        for (int i = 0; i < names.size(); i++) {
            int index = i;
            boolean first = i == 0;
            boolean last = i == names.size() - 1;
            JToggleButton cell = new JToggleButton(names.get(i)) {
                // Painted here, not by the look and feel, so the chosen cell is always the accent
                // colour and the row reads as one control: only its two outer ends are rounded.
                @Override
                protected void paintComponent(java.awt.Graphics g) {
                    java.awt.Graphics2D g2 = (java.awt.Graphics2D) g.create();
                    g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setRenderingHint(java.awt.RenderingHints.KEY_TEXT_ANTIALIASING, java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                    int w = getWidth();
                    int h = getHeight();
                    int arc = Zoom.px(Style.ARC);
                    java.awt.geom.Area shape = new java.awt.geom.Area(new java.awt.geom.RoundRectangle2D.Double(0, 0, w - 1, h - 1, arc, arc));
                    if (!first) { // square on the left
                        shape.add(new java.awt.geom.Area(new java.awt.Rectangle(0, 0, arc, h - 1)));
                    }
                    if (!last) { // and on the right
                        shape.add(new java.awt.geom.Area(new java.awt.Rectangle(w - 1 - arc, 0, arc, h - 1)));
                    }
                    g2.setColor(isSelected() ? Style.ACCENT : getModel().isPressed() ? Style.PRESSED
                            : getModel().isRollover() ? Style.HOVER : Style.BAR);
                    g2.fill(shape);
                    g2.setColor(Style.CONTROL_BORDER);
                    g2.draw(shape);
                    if (isFocusOwner()) {
                        g2.setColor(Style.ACCENT);
                        g2.setStroke(new java.awt.BasicStroke(Style.FOCUS_WIDTH));
                        g2.draw(new java.awt.geom.RoundRectangle2D.Double(1, 1, w - 3, h - 3, arc, arc));
                    }
                    g2.setFont(Zoom.scaled(Style.sans(isSelected(), 12.5f)));
                    java.awt.FontMetrics m = g2.getFontMetrics();
                    g2.setColor(isSelected() ? Style.ON_ACCENT : Style.INK_SOFT);
                    g2.drawString(getText(), (w - m.stringWidth(getText())) / 2, (h - m.getHeight()) / 2 + m.getAscent());
                    g2.dispose();
                }
            };
            cell.setRolloverEnabled(true);
            cell.setContentAreaFilled(false);
            cell.setBorderPainted(false);
            // Measured at 100%, and scaled by Zoom (now and after each zoom).
            Zoom.size(cell, Math.max(52, (int) Style.textWidth(names.get(i), Style.sans(true, 12.5f)) + 20), Style.CONTROL_H);
            cell.addActionListener(e -> chosen.accept(index));
            group.add(cell);
            cells.add(cell);
            add(cell);
        }
    }

    public void choose(int index) {
        for (int i = 0; i < cells.size(); i++) {
            cells.get(i).setSelected(i == index);
        }
    }

    // Says what the cells are for, to a screen reader and in their tooltips.
    public void describe(String what) {
        for (JToggleButton cell : cells) {
            cell.setToolTipText(cell.getText() + " " + what);
            cell.getAccessibleContext().setAccessibleDescription(what);
        }
    }
}
