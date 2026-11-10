package raphael;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.awt.AWTException;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Window;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import javax.swing.AbstractButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

// Pictures of the real window on the computer it runs on, for checking the interface on systems
// and screens the developer doesn't have: Windows at 150% scaling and a Mac, in CI
// (.github/workflows/package.yml). Not part of a normal "mvn test" (tagged "screenshots").
// Each picture is taken twice: as the screen shows it (with the system's scaling, when the
// screen can be captured) and as Swing paints it.
@Tag("screenshots")
class ScreenshotTest {

    private final Path out = Path.of("target", "screenshots");

    @Test
    void theMainWindowAndItsDialogs() throws Exception {
        Files.createDirectories(out);
        App.main(new String[0]);
        JFrame window = waitFor(JFrame.class);
        assertNotNull(window, "the window opened");
        Thread.sleep(1500);
        dismissDialogs();
        String theme = raphael.render.Style.isDark() ? "dark" : "light";
        take(window, theme + "-welcome");
        openExample(window, "Trees", "BST insert");
        Thread.sleep(800);
        take(window, theme + "-guide");
        click(window, "Run");
        Thread.sleep(6000);
        take(window, theme + "-run");
        for (String name : new String[] {"Settings", "Keyboard shortcuts"}) {
            click(window, name);
            JDialog dialog = waitFor(JDialog.class);
            if (dialog != null) {
                Thread.sleep(500);
                take(dialog, theme + "-" + name.toLowerCase().replace(' ', '-'));
                SwingUtilities.invokeAndWait(dialog::dispose);
            }
        }
    }

    private void take(Window window, String name) throws Exception {
        BufferedImage painted = new BufferedImage(window.getWidth(), window.getHeight(), BufferedImage.TYPE_INT_RGB);
        SwingUtilities.invokeAndWait(() -> {
            Graphics2D g = painted.createGraphics();
            window.paint(g);
            g.dispose();
        });
        ImageIO.write(painted, "png", out.resolve(name + "-painted.png").toFile());
        try { // the screen itself, at the system's real resolution, where it can be read
            Rectangle area = window.getBounds();
            java.util.List<java.awt.Image> sizes = new Robot().createMultiResolutionScreenCapture(area).getResolutionVariants();
            java.awt.Image screen = sizes.get(sizes.size() - 1); // the largest: the real pixels
            BufferedImage shot = new BufferedImage(screen.getWidth(null), screen.getHeight(null), BufferedImage.TYPE_INT_RGB);
            shot.getGraphics().drawImage(screen, 0, 0, null);
            ImageIO.write(shot, "png", out.resolve(name + "-screen.png").toFile());
        } catch (AWTException | SecurityException | UnsupportedOperationException e) {
            System.out.println("no screen capture here: " + e);
        }
    }

    // Opens an example through File → Open example → group → title, as a user would.
    static void openExample(JFrame window, String group, String title) throws Exception {
        javax.swing.JMenu file = window.getJMenuBar().getMenu(0);
        for (int i = 0; i < file.getItemCount(); i++) {
            if (file.getItem(i) instanceof javax.swing.JMenu && file.getItem(i).getText().equals("Open example")) {
                javax.swing.JMenu examples = (javax.swing.JMenu) file.getItem(i);
                for (Component part : examples.getMenuComponents()) {
                    if (part instanceof javax.swing.JMenu && ((javax.swing.JMenu) part).getText().equals(group)) {
                        javax.swing.JMenu menu = (javax.swing.JMenu) part;
                        for (int k = 0; k < menu.getItemCount(); k++) {
                            if (menu.getItem(k).getText().equals(title)) {
                                SwingUtilities.invokeAndWait(menu.getItem(k)::doClick);
                                return;
                            }
                        }
                    }
                }
            }
        }
        throw new IllegalArgumentException("no example " + group + " → " + title);
    }

    private static void click(Container in, String name) {
        AbstractButton button = find(in, name);
        if (button != null) {
            SwingUtilities.invokeLater(button::doClick);
        }
    }

    private static AbstractButton find(Container in, String name) {
        for (Component part : in.getComponents()) {
            if (!part.isShowing()) {
                continue; // a tab behind the one in front
            }
            if (part instanceof AbstractButton && (name.equals(((AbstractButton) part).getText())
                    || name.equals(part.getAccessibleContext().getAccessibleName()))) {
                return (AbstractButton) part;
            }
            if (part instanceof Container) {
                AbstractButton found = find((Container) part, name);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static void dismissDialogs() throws Exception {
        for (Window w : Window.getWindows()) {
            if (w instanceof JDialog && w.isShowing()) {
                SwingUtilities.invokeAndWait(w::dispose);
            }
        }
    }

    private static <T extends Window> T waitFor(Class<T> type) throws InterruptedException {
        for (int i = 0; i < 100; i++) {
            for (Window w : Window.getWindows()) {
                if (type.isInstance(w) && w.isShowing()) {
                    return type.cast(w);
                }
            }
            Thread.sleep(100);
        }
        return null;
    }
}
