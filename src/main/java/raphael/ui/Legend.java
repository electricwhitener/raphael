package raphael.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.JComponent;
import raphael.render.Style;

// The key to the picture's marks, at the right of the status bar: a small swatch, then what it
// means. Each mark is a shape or pattern as well as a colour (filled, outlined, dashed), so the
// key works for someone who can't tell the colours apart. Colours are read from Style while
// painting, so the key follows dark mode by itself.
// OOP: inheritance (extends JComponent) and overriding (paintComponent, getPreferredSize).
public class Legend extends JComponent {

    private static final String[] NAMES = {"Next", "Just ran", "Just changed", "Writes", "Reads"};
    private static final int SWATCH = 10;
    private static final int SWATCH_GAP = 6; // between a swatch and its name
    private static final int ITEM_GAP = 16;  // between two items

    // Measured and drawn at 100% on a Graphics scaled by the interface's zoom: swatches, gaps and
    // text grow together.
    private final Font font = Style.sans(false, Style.UI_SMALL);

    @Override
    public Dimension getPreferredSize() {
        FontMetrics metrics = getFontMetrics(font);
        int width = 0;
        for (String name : NAMES) {
            width += SWATCH + SWATCH_GAP + metrics.stringWidth(name) + ITEM_GAP;
        }
        return new Dimension(Zoom.px(width - ITEM_GAP + 2 * Style.SPACE_3), Zoom.px(Style.STATUS_H));
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.scale(Zoom.factor(), Zoom.factor());
        FontMetrics metrics = g.getFontMetrics(font);
        int middle = (int) (getHeight() / Zoom.factor() / 2);
        int x = Style.SPACE_3;
        for (int i = 0; i < NAMES.length; i++) {
            int top = middle - SWATCH / 2;
            switch (i) {
                case 0 -> fill(g, x, top, Style.ACCENT);
                case 1 -> fill(g, x, top, Style.OUTLINE);
                case 2 -> fill(g, x, top, Style.CHANGE);
                case 3 -> { // a cell being written: tinted, with a solid outline
                    fill(g, x, top, Style.ACCENT_TINT);
                    g.setColor(Style.ACCENT);
                    g.setStroke(new BasicStroke(1.5f));
                    g.drawRect(x, top, SWATCH - 1, SWATCH - 1);
                }
                default -> { // a cell being read: a dashed outline
                    g.setColor(Style.ACCENT);
                    g.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10, new float[] {2, 2}, 0));
                    g.drawRect(x, top, SWATCH - 1, SWATCH - 1);
                }
            }
            x += SWATCH + SWATCH_GAP;
            g.setColor(Style.INK_SOFT);
            g.setFont(font);
            g.drawString(NAMES[i], x, middle + (metrics.getAscent() - metrics.getDescent()) / 2);
            x += metrics.stringWidth(NAMES[i]) + ITEM_GAP;
        }
        g.dispose();
    }

    // A filled swatch with a faint edge, so a pale colour (amber on the light bar) still shows.
    private static void fill(Graphics2D g, int x, int y, Color color) {
        g.setColor(color);
        g.fillRect(x, y, SWATCH, SWATCH);
        g.setColor(new Color(Style.OUTLINE.getRed(), Style.OUTLINE.getGreen(), Style.OUTLINE.getBlue(), 150));
        g.setStroke(new BasicStroke(1f));
        g.drawRect(x, y, SWATCH - 1, SWATCH - 1);
    }
}
