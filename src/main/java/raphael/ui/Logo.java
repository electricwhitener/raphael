package raphael.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

// Raphael's icon: a blue rounded square with a small white binary tree, its newest node in
// amber (Raphael's colour for "just changed"). The window shows it in the title bar and taskbar,
// and scripts/AppIcon.java writes it into the installers' icon files. It's drawn at each size
// rather than shrunk from one big picture, so small sizes stay sharp. The colours are fixed, not
// read from Style: an app's icon doesn't change with its theme.
// OOP: a utility class (static methods, private constructor).
public final class Logo {

    private static final Color BLUE = new Color(0x0969DA);
    private static final Color AMBER = new Color(0xF2CC60);

    private Logo() {
    }

    public static BufferedImage draw(int size) {
        return draw(size, false);
    }

    // mac: the macOS version, which sits on Apple's icon grid (a tile 80% of the canvas, with
    // rounder corners) so it matches the other icons in the Dock.
    // At 24 px and below, the tree has three nodes and thicker lines, so it's still a tree at 16 px.
    public static BufferedImage draw(int size, boolean mac) {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.scale(size / 100.0, size / 100.0); // drawn on a 100 × 100 grid
        g.setColor(BLUE);
        if (mac) {
            g.fill(new RoundRectangle2D.Double(10, 10, 80, 80, 36, 36));
            g.translate(50, 50); // the tree shrinks with the tile, about its centre
            g.scale(0.88, 0.88);
            g.translate(-50, -50);
        } else {
            g.fill(new RoundRectangle2D.Double(4, 4, 92, 92, 24, 24));
        }

        boolean small = size <= 24;
        double[][] nodes = small
                ? new double[][] {{50, 28}, {28, 68}, {72, 68}}            // the newest node is the right child
                : new double[][] {{50, 24}, {26, 50}, {74, 50}, {63, 77}}; // the newest hangs under the right
        int[][] edges = small ? new int[][] {{0, 1}, {0, 2}} : new int[][] {{0, 1}, {0, 2}, {2, 3}};
        double r = small ? 13 : 10.5;
        g.setColor(Color.WHITE);
        g.setStroke(new BasicStroke(small ? 9f : 7f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (int[] edge : edges) {
            g.draw(new Line2D.Double(nodes[edge[0]][0], nodes[edge[0]][1], nodes[edge[1]][0], nodes[edge[1]][1]));
        }
        for (int i = 0; i < nodes.length; i++) {
            g.setColor(i == nodes.length - 1 ? AMBER : Color.WHITE);
            g.fill(new Ellipse2D.Double(nodes[i][0] - r, nodes[i][1] - r, 2 * r, 2 * r));
        }
        g.dispose();
        return image;
    }

    // The sizes a window icon is shown at, so the system can pick the sharpest.
    public static List<Image> windowIcons() {
        List<Image> icons = new ArrayList<>();
        for (int size : new int[] {16, 20, 24, 32, 40, 48, 64, 128, 256}) {
            icons.add(draw(size));
        }
        return icons;
    }
}
