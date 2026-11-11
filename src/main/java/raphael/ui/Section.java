package raphael.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Insets;
import java.awt.font.TextAttribute;
import java.util.Locale;
import java.util.Map;
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
        Zoom.font(label, Style.SECTION_FONT.deriveFont(Map.of(TextAttribute.TRACKING, 0.08)));
        label.setBorder(Zoom.gap(Style.SPACE_2, Style.SPACE_3, Style.SPACE_1, Style.SPACE_3));
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
    public static void addClose(JComponent panel, String tip, Runnable close) {
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
        JPanel row = new JPanel(new BorderLayout());
        row.setOpaque(false);
        wrapped.remove(heading);
        row.add(heading, BorderLayout.CENTER);
        row.add(button, BorderLayout.EAST);
        wrapped.add(row, BorderLayout.NORTH);
    }

    private static boolean headed(JComponent panel) {
        return panel.getLayout() instanceof BorderLayout
                && ((BorderLayout) panel.getLayout()).getLayoutComponent(BorderLayout.NORTH) != null;
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
