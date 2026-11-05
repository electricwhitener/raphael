package raphael;

import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Window;
import java.awt.image.BufferedImage;
import java.awt.image.IndexColorModel;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageOutputStream;
import javax.swing.AbstractButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;

// Makes docs/demo.gif, the animation at the top of the README: the real window reversing a
// linked list. A tool for the developer, not a test: run it with
//   mvn -q test-compile && java -cp "target/test-classes:target/app/*" raphael.DemoGif docs/demo.gif
// A GIF has at most 256 colours, so the most common colours of the frames become its palette.
// Frames that look the same as the one before are merged into it, so pauses cost nothing.
public class DemoGif {

    private static final int FRAME_MS = 100;  // 10 frames a second
    private static final int SECONDS = 12;
    private static final double SCALE = 0.7; // of the window's size

    public static void main(String[] args) throws Exception {
        String out = args.length > 0 ? args[0] : "docs/demo.gif";
        App.SETTINGS.putBoolean("dark", false);
        App.main(new String[0]);
        JFrame window = null;
        while (window == null) {
            Thread.sleep(100);
            for (Window w : Window.getWindows()) {
                if (w instanceof JFrame && w.isShowing()) {
                    window = (JFrame) w;
                }
            }
        }
        Thread.sleep(1500);
        click(window, "ReverseList");
        Thread.sleep(800);
        JComboBox<?> speed = find(window, JComboBox.class);
        SwingUtilities.invokeAndWait(() -> speed.setSelectedIndex(2)); // 2×
        click(window, "Run");
        Thread.sleep(2500); // recorded; it plays by itself

        // The toolbar, the code and the picture: the window down to the bottom panels.
        Component picture = find(window, raphael.ui.VisualPanel.class);
        java.awt.Insets border = window.getInsets(); // the window's own border, left out
        int width = window.getWidth() - border.left - border.right;
        int height = SwingUtilities.convertPoint(picture, 0, picture.getHeight(), window).y - border.top;
        List<BufferedImage> frames = new ArrayList<>();
        List<Integer> delays = new ArrayList<>();
        JFrame shown = window;
        for (int i = 0; i < SECONDS * 1000 / FRAME_MS; i++) {
            BufferedImage full = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            SwingUtilities.invokeAndWait(() -> {
                Graphics2D g = full.createGraphics();
                g.translate(-border.left, -border.top);
                shown.paint(g);
                g.dispose();
            });
            BufferedImage frame = new BufferedImage((int) (full.getWidth() * SCALE), (int) (full.getHeight() * SCALE), BufferedImage.TYPE_INT_RGB);
            Graphics2D g = frame.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.drawImage(full, 0, 0, frame.getWidth(), frame.getHeight(), null);
            g.dispose();
            if (!frames.isEmpty() && same(frames.get(frames.size() - 1), frame)) {
                delays.set(delays.size() - 1, delays.get(delays.size() - 1) + FRAME_MS);
            } else {
                frames.add(frame);
                delays.add(FRAME_MS);
            }
            Thread.sleep(FRAME_MS);
        }
        write(frames, delays, new File(out));
        System.out.println(frames.size() + " frames written to " + out + " (" + new File(out).length() / 1024 + " KB)");
        System.exit(0);
    }

    private static void write(List<BufferedImage> frames, List<Integer> delays, File file) throws Exception {
        IndexColorModel palette = palette(frames);
        ImageWriter writer = ImageIO.getImageWritersByFormatName("gif").next();
        try (ImageOutputStream stream = ImageIO.createImageOutputStream(file)) {
            writer.setOutput(stream);
            writer.prepareWriteSequence(null);
            for (int i = 0; i < frames.size(); i++) {
                BufferedImage indexed = indexed(frames.get(i), palette);
                IIOMetadata metadata = writer.getDefaultImageMetadata(ImageTypeSpecifier.createFromRenderedImage(indexed), null);
                String format = metadata.getNativeMetadataFormatName();
                IIOMetadataNode root = (IIOMetadataNode) metadata.getAsTree(format);
                IIOMetadataNode control = child(root, "GraphicControlExtension");
                control.setAttribute("disposalMethod", "none");
                control.setAttribute("userInputFlag", "FALSE");
                control.setAttribute("transparentColorFlag", "FALSE");
                control.setAttribute("delayTime", String.valueOf(delays.get(i) / 10)); // in hundredths of a second
                control.setAttribute("transparentColorIndex", "0");
                // Each frame carries the colour table itself, or the writer may use a default one.
                IIOMetadataNode descriptor = child(root, "ImageDescriptor");
                IIOMetadataNode table = new IIOMetadataNode("LocalColorTable");
                table.setAttribute("sizeOfLocalColorTable", "256");
                table.setAttribute("sortFlag", "FALSE");
                for (int c = 0; c < 256; c++) {
                    IIOMetadataNode entry = new IIOMetadataNode("ColorTableEntry");
                    entry.setAttribute("index", String.valueOf(c));
                    entry.setAttribute("red", String.valueOf(palette.getRed(c)));
                    entry.setAttribute("green", String.valueOf(palette.getGreen(c)));
                    entry.setAttribute("blue", String.valueOf(palette.getBlue(c)));
                    table.appendChild(entry);
                }
                for (int k = root.getLength() - 1; k >= 0; k--) {
                    if (root.item(k).getNodeName().equals("LocalColorTable")) {
                        root.removeChild(root.item(k));
                    }
                }
                root.insertBefore(table, descriptor.getNextSibling());
                if (i == 0) { // play forever
                    IIOMetadataNode application = new IIOMetadataNode("ApplicationExtension");
                    application.setAttribute("applicationID", "NETSCAPE");
                    application.setAttribute("authenticationCode", "2.0");
                    application.setUserObject(new byte[] {1, 0, 0});
                    child(root, "ApplicationExtensions").appendChild(application);
                }
                metadata.setFromTree(format, root);
                writer.writeToSequence(new IIOImage(indexed, null, metadata), null);
            }
            writer.endWriteSequence();
        }
    }

    // The 256 most common colours (each rounded to 5 bits per channel), over every frame.
    private static IndexColorModel palette(List<BufferedImage> frames) {
        Map<Integer, Integer> counts = new HashMap<>();
        for (BufferedImage frame : frames) {
            for (int y = 0; y < frame.getHeight(); y += 2) {
                for (int x = 0; x < frame.getWidth(); x += 2) {
                    counts.merge(frame.getRGB(x, y) & 0xF8F8F8, 1, Integer::sum);
                }
            }
        }
        List<Integer> common = counts.entrySet().stream().sorted((a, b) -> b.getValue() - a.getValue())
                .limit(256).map(Map.Entry::getKey).toList();
        byte[] r = new byte[256];
        byte[] g = new byte[256];
        byte[] b = new byte[256];
        for (int i = 0; i < common.size(); i++) {
            int c = common.get(i) | 0x040404; // the middle of each rounded range
            r[i] = (byte) (c >> 16);
            g[i] = (byte) (c >> 8);
            b[i] = (byte) c;
        }
        return new IndexColorModel(8, 256, r, g, b);
    }

    private static BufferedImage indexed(BufferedImage frame, IndexColorModel palette) {
        BufferedImage indexed = new BufferedImage(frame.getWidth(), frame.getHeight(), BufferedImage.TYPE_BYTE_INDEXED, palette);
        Map<Integer, Integer> nearest = new HashMap<>();
        for (int y = 0; y < frame.getHeight(); y++) {
            for (int x = 0; x < frame.getWidth(); x++) {
                int rgb = frame.getRGB(x, y) & 0xFFFFFF;
                int index = nearest.computeIfAbsent(rgb, c -> closest(c, palette));
                indexed.getRaster().setSample(x, y, 0, index);
            }
        }
        return indexed;
    }

    private static int closest(int rgb, IndexColorModel palette) {
        int best = 0;
        long bestDistance = Long.MAX_VALUE;
        for (int i = 0; i < palette.getMapSize(); i++) {
            long dr = ((rgb >> 16) & 255) - palette.getRed(i);
            long dg = ((rgb >> 8) & 255) - palette.getGreen(i);
            long db = (rgb & 255) - palette.getBlue(i);
            long distance = 3 * dr * dr + 4 * dg * dg + 2 * db * db;
            if (distance < bestDistance) {
                bestDistance = distance;
                best = i;
            }
        }
        return best;
    }

    private static boolean same(BufferedImage a, BufferedImage b) {
        for (int y = 0; y < a.getHeight(); y += 3) {
            for (int x = 0; x < a.getWidth(); x += 3) {
                if (a.getRGB(x, y) != b.getRGB(x, y)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static IIOMetadataNode child(IIOMetadataNode root, String name) {
        for (int i = 0; i < root.getLength(); i++) {
            if (root.item(i).getNodeName().equals(name)) {
                return (IIOMetadataNode) root.item(i);
            }
        }
        IIOMetadataNode node = new IIOMetadataNode(name);
        root.appendChild(node);
        return node;
    }

    private static void click(Container in, String name) {
        AbstractButton button = find(in, AbstractButton.class, name);
        if (button != null) {
            SwingUtilities.invokeLater(button::doClick);
        }
    }

    private static <T extends Component> T find(Container in, Class<T> type) {
        return find(in, type, null);
    }

    private static <T extends Component> T find(Container in, Class<T> type, String text) {
        for (Component part : in.getComponents()) {
            if (type.isInstance(part) && (text == null || (part instanceof AbstractButton
                    && (text.equals(((AbstractButton) part).getText())
                    || text.equals(part.getAccessibleContext().getAccessibleName()))))) {
                return type.cast(part);
            }
            if (part instanceof Container) {
                T found = find((Container) part, type, text);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }
}
