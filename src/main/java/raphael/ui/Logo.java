package raphael.ui;

import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;

// Raphael's icon: the character art in logo/raphael.jpg, on a rounded tile like an app icon, so it
// sits well on light and dark backgrounds alike. The window shows it in the title bar and taskbar,
// the welcome page in its header, and scripts/AppIcon.java writes it into the installers' icon
// files. At 32 px and below only the face is shown, because the whole figure would be a blur.
// OOP: a utility class (static methods, private constructor); the picture is read once and kept.
public final class Logo {

    private static final int SMALL = 32;
    private static BufferedImage art;

    private Logo() {
    }

    public static BufferedImage draw(int size) {
        return draw(size, false);
    }

    // mac: the macOS version, which sits on Apple's icon grid (a tile 80% of the canvas, with
    // rounder corners) so it matches the other icons in the Dock.
    public static BufferedImage draw(int size, boolean mac) {
        BufferedImage source = art();
        // The face: the middle of the picture, from the eyes to the chin.
        BufferedImage picture = size <= SMALL ? source.getSubimage(75, 60, 250, 250) : source;
        double inset = size * (mac ? 0.10 : 0.04);
        double tile = size - 2 * inset;
        double arc = tile * (mac ? 0.45 : 0.26);
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        // A rounded tile with smooth edges: paint the tile, then keep the picture only where it is.
        g.fill(new RoundRectangle2D.Double(inset, inset, tile, tile, arc, arc));
        g.setComposite(java.awt.AlphaComposite.SrcIn);
        int edge = (int) Math.round(tile);
        g.drawImage(shrink(picture, edge), (int) Math.round(inset), (int) Math.round(inset), null);
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

    private static synchronized BufferedImage art() {
        if (art == null) {
            try (InputStream in = Logo.class.getResourceAsStream("/logo/raphael.jpg")) {
                if (in == null) {
                    throw new IllegalStateException("logo/raphael.jpg is missing from Raphael");
                }
                art = ImageIO.read(in);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return art;
    }

    // Shrinks a picture to size × size in halving steps: one big jump would skip most of the
    // pixels and leave the small icons jagged.
    private static BufferedImage shrink(BufferedImage picture, int size) {
        BufferedImage current = picture;
        int width = picture.getWidth();
        do {
            width = Math.max(size, width / 2);
            BufferedImage next = new BufferedImage(width, width, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = next.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(current, 0, 0, width, width, null);
            g.dispose();
            current = next;
        } while (width > size);
        return current;
    }
}
