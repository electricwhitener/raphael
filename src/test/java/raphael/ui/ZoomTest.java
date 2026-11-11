package raphael.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.formdev.flatlaf.util.UIScale;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Rectangle;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JList;
import javax.swing.JPanel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

// The interface's zoom: Raphael's own sizes, fonts, gaps and struts follow it, and come back
// exactly at 100%; scaling a part twice changes nothing.
class ZoomTest {

    @AfterEach
    void backTo100() {
        UIScale.setZoomFactor(1f);
    }

    @Test
    void sizesFontsAndRowsFollowTheZoom() {
        JButton button = new JButton();
        Zoom.size(button, 32, 32);
        JPanel label = new JPanel();
        Zoom.font(label, new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        JList<String> list = new JList<>();
        Zoom.rowHeight(list, 20);
        JPanel panel = new JPanel(new BorderLayout(8, 4));
        panel.add(button);
        panel.add(label);
        panel.add(list);
        UIScale.setZoomFactor(1.5f);
        Zoom.reapply(panel);
        Zoom.reapply(panel); // twice: the same
        assertEquals(new Dimension(48, 48), button.getPreferredSize());
        assertEquals(18f, label.getFont().getSize2D(), 0.01);
        assertEquals(30, list.getFixedCellHeight());
        assertEquals(12, ((BorderLayout) panel.getLayout()).getHgap());
        assertEquals(6, ((BorderLayout) panel.getLayout()).getVgap());
        UIScale.setZoomFactor(1f);
        Zoom.reapply(panel);
        assertEquals(new Dimension(32, 32), button.getPreferredSize());
        assertEquals(12f, label.getFont().getSize2D(), 0.01);
        assertEquals(20, list.getFixedCellHeight());
        assertEquals(8, ((BorderLayout) panel.getLayout()).getHgap());
    }

    @Test
    void strutsKeepTheirBaseAndBigSizesStayBig() {
        JPanel column = new JPanel();
        Box.Filler strut = (Box.Filler) Box.createVerticalStrut(10);
        column.add(strut);
        UIScale.setZoomFactor(2f);
        Zoom.reapply(column);
        assertEquals(20, strut.getPreferredSize().height);
        assertEquals(Short.MAX_VALUE, strut.getMaximumSize().width, "\"as wide as it likes\" isn't scaled");
        UIScale.setZoomFactor(1f);
        Zoom.reapply(column);
        assertEquals(10, strut.getPreferredSize().height);
    }

    @Test
    void zoomingInStopsWhereTheScreenHasNoRoom() {
        Rectangle laptop = new Rectangle(0, 0, 1366, 728);
        assertTrue(Zoom.fitsAt(1f, laptop));
        assertTrue(Zoom.fitsAt(1.1f, laptop));
        assertFalse(Zoom.fitsAt(1.5f, laptop), "960 × 1.5 = 1440 is wider than the screen");
        assertTrue(Zoom.fitsAt(2f, new Rectangle(0, 0, 1920, 1200)));
        assertTrue(Zoom.next() > 1f);
    }
}
