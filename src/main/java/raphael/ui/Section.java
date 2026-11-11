package raphael.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Insets;
import java.util.Locale;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.AbstractBorder;
import javax.swing.border.Border;
import raphael.render.Style;

// Small helpers for the window's terminal-style look: section headers like "CALL STACK"
// (small grey capitals, spaced out) and thin grey borders.
// Their colours are read when they paint, so they follow dark mode by themselves.
// OOP: a utility class (static methods, private constructor), with an anonymous subclass of JLabel
// and a nested subclass of AbstractBorder that override how they get their colour.
public final class Section {

    private Section() {
    }

    public static JLabel header(String text) {
        JLabel label = new JLabel(text.toUpperCase(Locale.ROOT)) {
            @Override
            public Color getForeground() {
                return Style.MUTED;
            }
        };
        Zoom.font(label, Style.sectionFont());
        // 9 + the text + 9 = STRIP_H (32), the height of the tab strips beside it, so the baselines line up.
        label.setBorder(Zoom.gap(9, Style.SPACE_3, 9, Style.SPACE_3));
        return label;
    }

    // A header on top of some content, with a thin line on its right to separate it from the next panel.
    public static JPanel wrap(JLabel header, JComponent body) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(Style.BACKGROUND);
        panel.add(header, BorderLayout.NORTH);
        panel.add(body, BorderLayout.CENTER);
        panel.setBorder(hairline(0, 0, 0, 1));
        return panel;
    }

    // An × at the right of a panel's heading (a panel built with wrap(), or one that holds one).
    public static JComponent addClose(JComponent panel, String tip, Runnable close) {
        JComponent wrapped = headed(panel) ? panel
                : panel.getComponentCount() > 0 && panel.getComponent(0) instanceof JComponent && headed((JComponent) panel.getComponent(0))
                ? (JComponent) panel.getComponent(0) : null;
        if (wrapped == null) {
            throw new IllegalArgumentException("no heading to put the close button on in " + panel.getClass().getSimpleName());
        }
        java.awt.Component heading = ((BorderLayout) wrapped.getLayout()).getLayoutComponent(BorderLayout.NORTH);
        javax.swing.JButton button = new javax.swing.JButton(Icons.of(Icons.Glyph.CLOSE, Icons.Role.TOOL));
        button.putClientProperty("JButton.buttonType", "toolBarButton");
        button.setToolTipText(tip);
        button.getAccessibleContext().setAccessibleName(tip);
        button.setRequestFocusEnabled(false);
        button.addActionListener(e -> close.run());
        Zoom.size(button, 24, 24); // a small square, centred in the 32 px heading
        JPanel holder = new JPanel(new java.awt.GridBagLayout());
        holder.setOpaque(false);
        holder.setBorder(Zoom.gap(0, 0, 0, Style.SPACE_1));
        holder.add(button);
        JPanel row = new JPanel(new BorderLayout());
        row.setOpaque(false);
        wrapped.remove(heading);
        row.add(heading, BorderLayout.CENTER);
        row.add(holder, BorderLayout.EAST);
        wrapped.add(row, BorderLayout.NORTH);
        return row; // the heading's row, for a right-click menu
    }

    private static boolean headed(JComponent panel) {
        return panel.getLayout() instanceof BorderLayout
                && ((BorderLayout) panel.getLayout()).getLayoutComponent(BorderLayout.NORTH) != null;
    }

    // How a row of a list or tree shows its state, the same everywhere: under the mouse, a grey
    // rounded fill; chosen, a darker grey with an ink bar at its left (so it isn't told by colour
    // alone). Drawn from y = top to the bottom of a width × height row.
    public static void paintRow(java.awt.Graphics2D g, int top, int width, int height, boolean chosen, boolean hovered) {
        if (!chosen && !hovered) {
            return;
        }
        g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
        int inset = Zoom.px(Style.ROW_INSET);
        int arc = Zoom.px(Style.ARC);
        g.setColor(chosen ? Style.SELECTED : Style.HOVER);
        g.fillRoundRect(inset, top + 1, width - 2 * inset, height - top - 2, arc, arc);
        if (chosen) {
            int bar = Zoom.px(Style.SELECT_BAR_W);
            int gap = Zoom.px(Style.SPACE_2) - 2;
            g.setColor(Style.INK);
            g.fillRoundRect(inset, top + gap, bar, Math.max(bar, height - top - 2 * gap), bar, bar);
        }
    }

    // A short muted sentence in the middle of an empty panel, saying what will appear there.
    // Wrapped to the panel's width, less a margin.
    public static void paintHint(Graphics g, JComponent on, String text) {
        java.awt.Graphics2D g2 = (java.awt.Graphics2D) g.create();
        g2.setRenderingHint(java.awt.RenderingHints.KEY_TEXT_ANTIALIASING, java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setFont(Zoom.scaled(Style.sans(false, Style.UI_SMALL)));
        g2.setColor(Style.MUTED);
        java.awt.FontMetrics m = g2.getFontMetrics();
        java.awt.Rectangle seen = on.getVisibleRect();
        int room = Math.max(m.stringWidth("Variables appear here"), seen.width - 2 * Zoom.px(Style.SPACE_6));
        java.util.List<String> lines = new java.util.ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            if (line.length() > 0 && m.stringWidth(line + " " + word) > room) {
                lines.add(line.toString());
                line.setLength(0);
            }
            line.append(line.length() > 0 ? " " : "").append(word);
        }
        lines.add(line.toString());
        int y = seen.y + (seen.height - lines.size() * m.getHeight()) / 2 + m.getAscent();
        for (String one : lines) {
            g2.drawString(one, seen.x + (seen.width - m.stringWidth(one)) / 2, y);
            y += m.getHeight();
        }
        g2.dispose();
    }

    // A thin grey line on the chosen sides (1 = draw, 0 = don't).
    public static Border hairline(int top, int left, int bottom, int right) {
        return new Hairline(top, left, bottom, right);
    }

    private static final class Hairline extends AbstractBorder {

        private final int top;
        private final int left;
        private final int bottom;
        private final int right;

        Hairline(int top, int left, int bottom, int right) {
            this.top = top;
            this.left = left;
            this.bottom = bottom;
            this.right = right;
        }

        @Override
        public Insets getBorderInsets(Component c, Insets insets) {
            insets.set(top, left, bottom, right);
            return insets;
        }

        @Override
        public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
            g.setColor(Style.HAIRLINE);
            g.fillRect(x, y, width, top);
            g.fillRect(x, y + height - bottom, width, bottom);
            g.fillRect(x, y, left, height);
            g.fillRect(x + width - right, y, right, height);
        }
    }
}
