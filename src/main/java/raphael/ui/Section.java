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
        label.setFont(Style.SECTION_FONT.deriveFont(Map.of(TextAttribute.TRACKING, 0.08)));
        label.setBorder(BorderFactory.createEmptyBorder(6, 8, 4, 8));
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
