import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;
import javax.swing.*;
public class ShotsW {
    static String OUT;
    static void snap(Component w, String name) throws Exception {
        SwingUtilities.invokeAndWait(() -> { try {
            BufferedImage img = new BufferedImage(w.getWidth(), w.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D g = img.createGraphics(); w.paint(g); g.dispose();
            ImageIO.write(img, "png", new File(OUT, name + ".png")); } catch (Exception e) { e.printStackTrace(); } });
    }
    static <T extends Component> T find(Container c, Class<T> type, int skip) {
        for (Component k : c.getComponents()) {
            if (type.isInstance(k)) { if (skip-- == 0) return type.cast(k); }
            if (k instanceof Container cc) { T r = find(cc, type, skip); if (r != null) return r; }
        }
        return null;
    }
    public static void main(String[] a) throws Exception {
        OUT = a[1];
        raphael.App.SETTINGS.putBoolean("dark", a[0].equals("dark"));
        raphael.App.SETTINGS.putLong("lastUpdateCheck", System.currentTimeMillis());
        raphael.App.main(new String[0]);
        Thread.sleep(2500);
        JFrame f0 = null; for (Window w : Window.getWindows()) if (w instanceof JFrame j && j.isShowing()) f0 = j; final JFrame f = f0;
        snap(f, a[0] + "-welcome");
        JList<?> groups = find(f, JList.class, 0);
        SwingUtilities.invokeAndWait(() -> groups.setSelectedIndex(10)); // Linked lists?
        Thread.sleep(400);
        snap(f, a[0] + "-welcome-group");
        SwingUtilities.invokeAndWait(() -> f.setSize(1000, 640));
        Thread.sleep(800);
        snap(f, a[0] + "-welcome-small");
        SwingUtilities.invokeAndWait(() -> f.setSize(1300, 840));
        Thread.sleep(500);
        // the Examples menu from the toolbar, with the Sorting submenu open
        for (Window w : Window.getWindows()) {}
        JButton ex = null;
        for (Component c : ((Container) f.getContentPane()).getComponents()) {}
        ex = findButton(f, "Examples");
        JButton exFinal = ex;
        SwingUtilities.invokeAndWait(exFinal::doClick);
        Thread.sleep(600);
        for (Window w : Window.getWindows()) if (w.isShowing() && w != f) snap(w, a[0] + "-menu");
        // MenuSelectionManager path to the Sorting submenu
        SwingUtilities.invokeAndWait(() -> {
            JPopupMenu popup = null;
            for (Window w : Window.getWindows()) if (w.isShowing() && w != null) { JPopupMenu p = find((Container) w, JPopupMenu.class, 0); if (p != null) popup = p; }
            if (popup == null) return;
            for (Component c : popup.getComponents()) if (c instanceof JMenu m && m.getText().equals("Sorting")) {
                MenuSelectionManager.defaultManager().setSelectedPath(new MenuElement[] {popup, m, m.getPopupMenu()});
            }
        });
        Thread.sleep(600);
        int k = 0;
        for (Window w : Window.getWindows()) if (w.isShowing() && w != f) snap(w, a[0] + "-submenu-" + (k++));
        System.exit(0);
    }
    static JButton findButton(Container c, String text) {
        for (Component k : c.getComponents()) {
            if (k instanceof JButton b && text.equals(b.getText())) return b;
            if (k instanceof Container cc) { JButton r = findButton(cc, text); if (r != null) return r; }
        }
        return null;
    }
}
