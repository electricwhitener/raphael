import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;

// Draws Raphael's app icon and writes it in each system's format:
//   raphael.png       512 px, for Linux
//   raphael.ico       16 to 256 px in one file, for Windows
//   raphael.iconset/  the sizes macOS wants; iconutil -c icns turns it into raphael.icns
// The drawing itself is raphael.ui.Logo, so the window and the installers show the same icon.
// Run it after compiling: java -cp target/classes scripts/AppIcon.java <folder>
public class AppIcon {

    public static void main(String[] args) throws IOException {
        Path out = Path.of(args.length > 0 ? args[0] : "target/icons");
        Files.createDirectories(out);
        ImageIO.write(draw(512), "png", out.resolve("raphael.png").toFile());

        List<byte[]> pngs = new ArrayList<>();
        int[] windowsSizes = {16, 24, 32, 48, 64, 128, 256};
        for (int size : windowsSizes) {
            pngs.add(png(draw(size)));
        }
        Files.write(out.resolve("raphael.ico"), ico(windowsSizes, pngs));

        Path iconset = Files.createDirectories(out.resolve("raphael.iconset"));
        for (int size : new int[] {16, 32, 128, 256, 512}) {
            ImageIO.write(draw(size), "png", iconset.resolve("icon_" + size + "x" + size + ".png").toFile());
            ImageIO.write(draw(size * 2), "png", iconset.resolve("icon_" + size + "x" + size + "@2x.png").toFile());
        }
        System.out.println("icons written to " + out);
    }

    private static BufferedImage draw(int size) {
        return raphael.ui.Logo.draw(size); // the one drawing, shared with the window's icon
    }

    private static byte[] png(BufferedImage image) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(image, "png", bytes);
        return bytes.toByteArray();
    }

    // An .ico file: a 6-byte header, a 16-byte entry per size, then each size as a PNG.
    // Numbers are little-endian; a width or height of 256 is written as 0.
    private static byte[] ico(int[] sizes, List<byte[]> pngs) {
        int offset = 6 + 16 * sizes.length;
        int total = offset + pngs.stream().mapToInt(p -> p.length).sum();
        ByteBuffer ico = ByteBuffer.allocate(total).order(ByteOrder.LITTLE_ENDIAN);
        ico.putShort((short) 0).putShort((short) 1).putShort((short) sizes.length);
        for (int i = 0; i < sizes.length; i++) {
            ico.put((byte) (sizes[i] >= 256 ? 0 : sizes[i])).put((byte) (sizes[i] >= 256 ? 0 : sizes[i]));
            ico.put((byte) 0).put((byte) 0).putShort((short) 1).putShort((short) 32);
            ico.putInt(pngs.get(i).length).putInt(offset);
            offset += pngs.get(i).length;
        }
        for (byte[] png : pngs) {
            ico.put(png);
        }
        return ico.array();
    }
}
