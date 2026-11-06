package raphael.ui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.RenderingHints;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import raphael.render.Style;

// What the picture area shows before anything has been recorded: what Raphael does, the three
// steps to use it, and examples to start from with one click. It replaces a single grey line
// ("press run…") that taught nothing and was hard to read.
// Every colour is read while painting, so the panel follows dark mode by itself.
// OOP: inheritance (extends JPanel); anonymous subclasses of JLabel and JComponent that override
// how they get their colour or paint; composition (it's built from labels, chips and buttons);
// a Consumer and a Runnable, so it doesn't need to know the window it's in.
public class EmptyState extends JPanel implements javax.swing.Scrollable {

    private static final int COLUMN = 440;
    private static final String[][] STEPS = {
        {"Paste or open a Java program", "Ctrl+V"},
        {"Press Run", "Ctrl+Enter"},
        {"Step with ← →, or play with Space", "← →"},
    };

    // canDrop: whether files can be dropped here. Not inside WSL, where Windows can't drag files
    // into Linux windows, so the guide doesn't promise it there.
    public EmptyState(String[] examples, boolean canDrop, Consumer<String> onExample, Runnable onMoreExamples) {
        super(new GridBagLayout()); // centres the column
        // At most 440 px wide, narrower in a small window, with 24 px to spare on each side.
        java.util.function.IntSupplier width = () -> getWidth() <= 0 ? COLUMN
                : Math.min(COLUMN, Math.max(240, getWidth() - 2 * Style.SPACE_6));
        JPanel column = new JPanel() {
            @Override
            public Dimension getPreferredSize() {
                return new Dimension(width.getAsInt(), super.getPreferredSize().height);
            }
        };
        column.setOpaque(false);
        column.setLayout(new BoxLayout(column, BoxLayout.Y_AXIS));

        column.add(label("Watch your program run", Style.sans(true, 20f), () -> Style.INK));
        column.add(Box.createVerticalStrut(Style.SPACE_2));
        column.add(label("Raphael records every line, then plays it back as a picture.", Style.sans(false, 13f), () -> Style.INK_SOFT));
        column.add(Box.createVerticalStrut(Style.SPACE_2 * 3));
        for (int i = 0; i < STEPS.length; i++) {
            column.add(step(i + 1, STEPS[i][0], Shortcuts.keycap(STEPS[i][1])));
            column.add(Box.createVerticalStrut(Style.SPACE_2));
        }
        column.add(Box.createVerticalStrut(Style.SPACE_6));
        column.add(label("Or start from an example", Style.sans(true, 12f), () -> Style.MUTED));
        column.add(Box.createVerticalStrut(Style.SPACE_2));
        JPanel chips = new JPanel(new WrapLayout(width, Style.SPACE_2, Style.SPACE_2));
        chips.setOpaque(false);
        for (int i = 0; i < Math.min(6, examples.length); i++) {
            String name = examples[i];
            chips.add(chip(name, () -> onExample.accept(name)));
        }
        chips.add(chip("More…", onMoreExamples));
        column.add(left(chips));
        if (canDrop) {
            column.add(Box.createVerticalStrut(Style.SPACE_3));
            column.add(label("You can also drop a .java file anywhere on this window.", Style.sans(false, 12f), () -> Style.MUTED));
        }

        add(column, new GridBagConstraints());
    }

    @Override
    public Color getBackground() {
        return Style.BACKGROUND;
    }

    // One numbered step: a numbered circle, what to do, and the keys for it.
    private static JComponent step(int number, String text, String keys) {
        JPanel row = new JPanel();
        row.setOpaque(false);
        row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
        row.add(new JComponent() {
            @Override
            protected void paintComponent(Graphics graphics) {
                Graphics2D g = smooth(graphics);
                g.setColor(Style.ACCENT_TINT);
                g.fillOval(0, 4, 24, 24);
                g.setColor(Style.ACCENT);
                g.setFont(Style.sans(true, 12f));
                String digit = String.valueOf(number);
                g.drawString(digit, 12 - g.getFontMetrics().stringWidth(digit) / 2, 21);
            }

            @Override
            public Dimension getPreferredSize() {
                return new Dimension(24, 32);
            }

            @Override
            public Dimension getMaximumSize() {
                return getPreferredSize();
            }
        });
        row.add(Box.createHorizontalStrut(Style.SPACE_3));
        row.add(label(text, Style.sans(false, 13f), () -> Style.INK));
        row.add(Box.createHorizontalGlue());
        row.add(keyChip(keys));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        return left(row);
    }

    // The keys, in a small rounded box like a key cap. Also used by the keyboard shortcuts list.
    static JLabel keyChip(String keys) {
        JLabel chip = new JLabel(keys) {
            @Override
            protected void paintComponent(Graphics graphics) {
                Graphics2D g = smooth(graphics);
                g.setColor(Style.BAR);
                g.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 6, 6);
                g.setColor(Style.HAIRLINE);
                g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 6, 6);
                super.paintComponent(graphics);
            }

            @Override
            public Color getForeground() {
                return Style.INK_SOFT;
            }
        };
        chip.setFont(Style.UI_FONT.deriveFont(12f));
        chip.setBorder(BorderFactory.createEmptyBorder(3, 8, 3, 8));
        return chip;
    }

    // A rounded button that loads an example.
    private static JButton chip(String text, Runnable action) {
        JButton chip = new JButton(text);
        chip.putClientProperty("FlatLaf.style", "arc: 999; margin: 4,12,4,12");
        chip.setFont(Style.sans(false, 13f));
        chip.addActionListener(e -> action.run());
        return chip;
    }

    // Inside a scroll pane: as wide as the window, and as tall too when there's room (so the
    // column stays centred); when there isn't, it scrolls.
    @Override
    public Dimension getPreferredScrollableViewportSize() {
        return getPreferredSize();
    }

    @Override
    public int getScrollableUnitIncrement(java.awt.Rectangle visible, int orientation, int direction) {
        return 16;
    }

    @Override
    public int getScrollableBlockIncrement(java.awt.Rectangle visible, int orientation, int direction) {
        return visible.height;
    }

    @Override
    public boolean getScrollableTracksViewportWidth() {
        return true;
    }

    @Override
    public boolean getScrollableTracksViewportHeight() {
        return getParent() != null && getParent().getHeight() > getPreferredSize().height;
    }

    private static JLabel label(String text, Font font, Supplier<Color> color) {
        JLabel label = new JLabel(text) {
            @Override
            public Color getForeground() {
                return color == null ? super.getForeground() : color.get();
            }
        };
        label.setFont(font);
        return left(label);
    }

    private static <T extends JComponent> T left(T component) {
        component.setAlignmentX(Component.LEFT_ALIGNMENT);
        return component;
    }

    private static Graphics2D smooth(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics;
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        return g;
    }

    // A FlowLayout that wraps onto a new row inside a BoxLayout column (a plain FlowLayout tells
    // its parent it needs one long row, so its chips would never wrap).
    private static class WrapLayout extends FlowLayout {

        private final java.util.function.IntSupplier columnWidth;

        WrapLayout(java.util.function.IntSupplier columnWidth, int gap, int vgap) {
            super(FlowLayout.LEFT, gap, vgap);
            this.columnWidth = columnWidth;
        }

        @Override
        public Dimension preferredLayoutSize(java.awt.Container target) {
            int width = columnWidth.getAsInt();
            int x = 0;
            int rows = 1;
            int rowHeight = 0;
            for (Component c : target.getComponents()) {
                Dimension size = c.getPreferredSize();
                if (x > 0 && x + size.width > width) {
                    rows++;
                    x = 0;
                }
                x += size.width + getHgap();
                rowHeight = Math.max(rowHeight, size.height);
            }
            return new Dimension(width, rows * (rowHeight + getVgap()));
        }
    }
}
