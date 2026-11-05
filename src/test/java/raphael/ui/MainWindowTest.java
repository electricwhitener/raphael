package raphael.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.awt.Container;
import java.awt.GraphicsEnvironment;
import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.KeyStroke;
import org.junit.jupiter.api.Test;

class MainWindowTest {

    // Swing offers a key to the panels around the focused one before the window's shortcuts. Scroll
    // panes and split panes normally take these keys, and then the shortcuts would never run.
    @Test
    void noPanelAroundThePictureTakesTheShortcutKeys() {
        assumeFalse(GraphicsEnvironment.isHeadless(), "needs a screen");
        MainWindow window = new MainWindow();
        for (Container around = window.getPicture().getParent(); around != null; around = around.getParent()) {
            InputMap keys = around instanceof JComponent
                    ? ((JComponent) around).getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT) : null;
            if (keys != null) { // null means it takes no keys at all
                for (String key : new String[] {"LEFT", "RIGHT", "HOME", "END"}) {
                    assertNull(keys.get(KeyStroke.getKeyStroke(key)), around.getClass().getSimpleName() + " takes " + key);
                }
            }
        }
        window.dispose();
    }

    // Save As offers the public class's name, which Java expects the file to have.
    @Test
    void saveSuggestsThePublicClassName() {
        assertEquals("BSTInsert.java", MainWindow.suggestedName("class Node {}\npublic class BSTInsert { }"));
        assertEquals("Node.java", MainWindow.suggestedName("class Node {}"));
        assertEquals("Main.java", MainWindow.suggestedName("// nothing yet"));
    }

    @Test
    void theLegendDrawsInBothThemes() {
        for (boolean dark : new boolean[] {false, true}) {
            raphael.render.Style.useTheme(dark);
            Legend legend = new Legend();
            legend.setSize(legend.getPreferredSize());
            java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(legend.getWidth(), legend.getHeight(),
                    java.awt.image.BufferedImage.TYPE_INT_RGB);
            legend.paint(image.getGraphics());
            assertTrue(legend.getPreferredSize().width < 520, "the key fits beside the status message");
        }
        raphael.render.Style.useTheme(false);
    }
}
