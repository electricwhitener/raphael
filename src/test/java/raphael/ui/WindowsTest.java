package raphael.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.awt.GraphicsEnvironment;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class WindowsTest {

    // New Window opens a second window; closing one leaves the other, which is then in front.
    // (Closing the last one ends Raphael, so the test doesn't.)
    @Test
    void windowsOpenAndCloseOneAtATime() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "needs a screen");
        SwingUtilities.invokeAndWait(() -> {
            try {
                MainWindow first = Windows.openNew();
                MainWindow second = Windows.openNew();
                assertEquals(2, Windows.all().size());
                assertSame(second, Windows.front(), "the newest window is in front");
                // (Where it opens is the system's call: macOS places new windows itself.)
                Windows.closed(second);
                assertEquals(1, Windows.all().size());
                assertSame(first, Windows.front());
            } finally {
                Windows.forgetAllForTests();
            }
        });
    }
}
