import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;
import javax.swing.*;
public class Shots8 {
    static String OUT;
    static void snap(Component w, String name) throws Exception {
        SwingUtilities.invokeAndWait(() -> { try {
            BufferedImage img = new BufferedImage(w.getWidth(), w.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D g = img.createGraphics(); w.paint(g); g.dispose();
            ImageIO.write(img, "png", new File(OUT, name + ".png")); } catch (Exception e) { e.printStackTrace(); } });
    }
    static JButton button(Container c, String text) {
        for (Component k : c.getComponents()) {
            if (k instanceof JButton b && (text.equals(b.getText()) || text.equals(b.getAccessibleContext().getAccessibleName()))) return b;
            if (k instanceof Container cc) { JButton r = button(cc, text); if (r != null) return r; }
        }
        return null;
    }
    static void example(JFrame f, String group, String title) throws Exception {
        JMenu file = f.getJMenuBar().getMenu(0);
        for (int i = 0; i < file.getItemCount(); i++)
            if (file.getItem(i) instanceof JMenu m && m.getText().equals("Open example"))
                for (Component part : m.getMenuComponents())
                    if (part instanceof JMenu g && g.getText().equals(group))
                        for (int k = 0; k < g.getItemCount(); k++)
                            if (g.getItem(k).getText().equals(title)) { JMenuItem it = g.getItem(k); SwingUtilities.invokeAndWait(it::doClick); return; }
        throw new IllegalStateException("no " + title);
    }
    public static void main(String[] a) throws Exception {
        OUT = a[1];
        raphael.App.SETTINGS.putBoolean("dark", a[0].equals("dark"));
        raphael.App.SETTINGS.putLong("lastUpdateCheck", System.currentTimeMillis());
        // a recording to open, and a recent list
        String src = raphael.io.ExampleCatalog.source(raphael.io.ExampleCatalog.find("BSTDelete"));
        var tr = new raphael.trace.JdiTracer().trace(new raphael.compile.SourceCompiler().compile(src), "");
        File rec = new File(a[1], "BSTDelete.raphael");
        raphael.io.RunFile.write(rec.toPath(), new raphael.io.RunFile.Saved(src, "", tr));
        System.out.println("recording: " + rec.length() / 1024 + " KB for " + tr.size() + " steps");
        raphael.App.SETTINGS.put("recentFiles", rec.getPath() + "\n" + new File("/home/amulb/raphael/src/main/resources/examples/sorting/QuickSort.java").getPath());
        raphael.App.main(new String[0]);
        Thread.sleep(2500);
        JFrame f0 = null; for (Window w : Window.getWindows()) if (w instanceof JFrame j && j.isShowing()) f0 = j; final JFrame f = f0;
        snap(f, a[0] + "-welcome-recent");
        example(f, "Files", "Write and read a file");
        Thread.sleep(700);
        snap(f, a[0] + "-files-before");
        SwingUtilities.invokeAndWait(button(f, "Run")::doClick);
        Thread.sleep(5000);
        JButton pause = button(f, "Pause");
        if (pause != null) SwingUtilities.invokeAndWait(pause::doClick);
        JButton first = button(f, "First step"), fwd = button(f, "Step forward");
        SwingUtilities.invokeAndWait(first::doClick);
        for (int i = 0; i < 4; i++) SwingUtilities.invokeAndWait(fwd::doClick);
        Thread.sleep(1200);
        snap(f, a[0] + "-files-empty");
        for (int i = 0; i < 7; i++) SwingUtilities.invokeAndWait(fwd::doClick);
        Thread.sleep(1200);
        snap(f, a[0] + "-files-filled");
        SwingUtilities.invokeAndWait(() -> ((raphael.ui.MainWindow) f).openFromSystem(rec));
        Thread.sleep(3000);
        snap(f, a[0] + "-recording");
        System.exit(0);
    }
}
