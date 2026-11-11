package raphael.ui;

import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.util.UIScale;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Window;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.JTable;

// The interface's zoom (Ctrl +, Ctrl −, Ctrl 0), as in VS Code: menus, toolbar, icons, panels,
// text and the picture all grow or shrink together. FlatLaf zooms its own parts (UIScale's zoom
// factor); Raphael's own fixed sizes and fonts are set through this class, which keeps each one's
// unzoomed value on the component, so a zoom can set them all again at the new scale.
// OOP: a utility class (static methods, private constructor); the base values live on each
// component as client properties, so nothing here holds on to a closed window.
public final class Zoom {

    private static final String SIZE = "raphael.zoom.size";
    private static final String MIN = "raphael.zoom.min";
    private static final String FONT = "raphael.zoom.font";
    private static final String ROW = "raphael.zoom.row";
    private static final String MAX = "raphael.zoom.max";
    private static final String GAPS = "raphael.zoom.gaps";   // a layout's gaps at 100%
    private static final String FILLER = "raphael.zoom.filler"; // a strut's sizes at 100%

    // A part whose sizes depend on its fonts or text (worked out when it's built): told to work
    // them out again after a zoom. Renderers that aren't in the window's tree can be one too.
    public interface Aware {
        void zoomChanged();
    }

    private Zoom() {
    }

    // Starts at the remembered zoom (before any window is made), and from then on scales every
    // part as it's added to the interface (a new tab, a dialog, a row of the welcome page): each is
    // built at its 100% sizes, and scaling it again later gives the same result.
    public static void restore() {
        float saved = raphael.App.SETTINGS.getFloat("zoom", 1f);
        if (!java.awt.GraphicsEnvironment.isHeadless()) {
            // A zoom chosen on a bigger screen: the largest that has room on this one.
            java.awt.Rectangle screen = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
            float[] steps = UIScale.getSupportedZoomFactors();
            for (int i = steps.length - 1; i >= 0 && saved > 1f && !fitsAt(saved, screen); i--) {
                saved = Math.min(saved, steps[i]);
            }
            saved = fitsAt(saved, screen) ? saved : 1f;
        }
        if (saved != 1f) {
            UIScale.setZoomFactor(saved);
        }
        java.awt.Toolkit.getDefaultToolkit().addAWTEventListener(event -> {
            if (event.getID() == java.awt.event.ContainerEvent.COMPONENT_ADDED && factor() != 1f) {
                reapply(((java.awt.event.ContainerEvent) event).getChild());
            }
        }, java.awt.AWTEvent.CONTAINER_EVENT_MASK);
    }

    // 1 at the normal size, 1.25 at 125%, and so on.
    public static float factor() {
        return UIScale.getZoomFactor();
    }

    // A length at this zoom (and the system's scaling, as FlatLaf's own sizes are).
    public static int px(int length) {
        return UIScale.scale(length);
    }

    public static float px(float length) {
        return UIScale.scale(length);
    }

    // An empty border (a gap round a panel) that follows the zoom: its insets are worked out at the
    // scale of the moment each time they're asked for. Not FlatLaf's FlatEmptyBorder: that one is a
    // UIResource, which the look and feel replaces on every theme switch or zoom.
    public static javax.swing.border.Border gap(int top, int left, int bottom, int right) {
        return new Gap(top, left, bottom, right);
    }

    private static final class Gap extends javax.swing.border.AbstractBorder {
        private final int top;
        private final int left;
        private final int bottom;
        private final int right;

        Gap(int top, int left, int bottom, int right) {
            this.top = top;
            this.left = left;
            this.bottom = bottom;
            this.right = right;
        }

        @Override
        public java.awt.Insets getBorderInsets(Component c, java.awt.Insets insets) {
            insets.set(px(top), px(left), px(bottom), px(right));
            return insets;
        }

        @Override
        public boolean isBorderOpaque() {
            return false;
        }
    }

    // A fixed preferred size that follows the zoom. A 0 stays 0 ("as the layout gives").
    public static void size(JComponent component, int width, int height) {
        component.putClientProperty(SIZE, new Dimension(width, height));
        component.setPreferredSize(new Dimension(px(width), px(height)));
    }

    // A fixed maximum size that follows the zoom.
    public static void maximum(JComponent component, int width, int height) {
        component.putClientProperty(MAX, new Dimension(width, height));
        component.setMaximumSize(new Dimension(huge(width) ? width : px(width), huge(height) ? height : px(height)));
    }

    private static boolean huge(int length) {
        return length >= Short.MAX_VALUE; // "as big as it likes": not scaled
    }

    // A fixed minimum size that follows the zoom.
    public static void minimum(JComponent component, int width, int height) {
        component.putClientProperty(MIN, new Dimension(width, height));
        component.setMinimumSize(new Dimension(px(width), px(height)));
    }

    // A font that follows the zoom: base is its size at 100%.
    public static void font(JComponent component, Font base) {
        component.putClientProperty(FONT, base);
        component.setFont(scaled(base));
    }

    public static Font scaled(Font base) {
        return base.deriveFont(px(base.getSize2D()));
    }

    // A list's or table's row height that follows the zoom.
    public static void rowHeight(JComponent component, int height) {
        component.putClientProperty(ROW, height);
        setRow(component, px(height));
    }

    private static void setRow(JComponent component, int height) {
        if (component instanceof javax.swing.JTree) {
            ((javax.swing.JTree) component).setRowHeight(height);
        } else if (component instanceof JList) {
            ((JList<?>) component).setFixedCellHeight(height);
        } else if (component instanceof JTable) {
            ((JTable) component).setRowHeight(height);
        }
    }

    private static Dimension scaled(Dimension base) {
        return new Dimension(huge(base.width) ? base.width : px(base.width), huge(base.height) ? base.height : px(base.height));
    }

    // A BorderLayout's, FlowLayout's or GridLayout's gaps, kept at 100% the first time.
    private static void scaleGaps(JComponent container) {
        java.awt.LayoutManager layout = container.getLayout();
        int[] base = (int[]) container.getClientProperty(GAPS);
        if (layout instanceof java.awt.BorderLayout) {
            java.awt.BorderLayout border = (java.awt.BorderLayout) layout;
            base = base != null ? base : keep(container, border.getHgap(), border.getVgap());
            border.setHgap(px(base[0]));
            border.setVgap(px(base[1]));
        } else if (layout instanceof java.awt.FlowLayout) {
            java.awt.FlowLayout flow = (java.awt.FlowLayout) layout;
            base = base != null ? base : keep(container, flow.getHgap(), flow.getVgap());
            flow.setHgap(px(base[0]));
            flow.setVgap(px(base[1]));
        } else if (layout instanceof java.awt.GridLayout) {
            java.awt.GridLayout grid = (java.awt.GridLayout) layout;
            base = base != null ? base : keep(container, grid.getHgap(), grid.getVgap());
            grid.setHgap(px(base[0]));
            grid.setVgap(px(base[1]));
        }
    }

    private static int[] keep(JComponent container, int hgap, int vgap) {
        int[] base = {hgap, vgap};
        container.putClientProperty(GAPS, base);
        return base;
    }

    // The window's smallest size at 100%: everything is laid out to fit in it.
    public static final int MIN_WIDTH = 960;
    public static final int MIN_HEIGHT = 600;

    // The next larger zoom (the same if it's the largest).
    public static float next() {
        for (float step : UIScale.getSupportedZoomFactors()) {
            if (step > factor() + 0.001f) {
                return step;
            }
        }
        return factor();
    }

    // Whether a window at this zoom still has its smallest size on this screen.
    public static boolean fitsAt(float zoom, java.awt.Rectangle screen) {
        return MIN_WIDTH * zoom <= screen.width && MIN_HEIGHT * zoom <= screen.height;
    }

    // Ctrl +, Ctrl −, Ctrl 0. True if the zoom changed (it stops at the largest and smallest).
    public static boolean in() {
        return changed(UIScale.zoomIn());
    }

    public static boolean out() {
        return changed(UIScale.zoomOut());
    }

    public static boolean reset() {
        return changed(UIScale.zoomReset());
    }

    private static boolean changed(boolean changed) {
        if (changed) {
            raphael.App.SETTINGS.putFloat("zoom", factor());
            raphael.App.setupLook(raphael.render.Style.isDark()); // the look again, its fonts at the new zoom
            FlatLaf.updateUI(); // FlatLaf's own parts, in every window
            for (Window window : Window.getWindows()) {
                reapply(window);
                window.validate();
                window.repaint();
            }
        }
        return changed;
    }

    // Sets every stored size, font and row height again, at the zoom now. Layout gaps and struts
    // are made at their 100% sizes, which are kept the first time they're seen; so this runs once
    // when a window or dialog is built, and again after each zoom.
    public static void reapply(Component component) {
        if (component instanceof javax.swing.Box.Filler) {
            javax.swing.Box.Filler filler = (javax.swing.Box.Filler) component;
            Dimension[] base = (Dimension[]) filler.getClientProperty(FILLER);
            if (base == null) {
                base = new Dimension[] {filler.getMinimumSize(), filler.getPreferredSize(), filler.getMaximumSize()};
                filler.putClientProperty(FILLER, base);
            }
            filler.changeShape(scaled(base[0]), scaled(base[1]), scaled(base[2]));
        }
        if (component instanceof Container && component instanceof JComponent) {
            scaleGaps((JComponent) component);
        }
        if (component instanceof JComponent) {
            JComponent c = (JComponent) component;
            Object max = c.getClientProperty(MAX);
            if (max instanceof Dimension) {
                Dimension base = (Dimension) max;
                c.setMaximumSize(new Dimension(huge(base.width) ? base.width : px(base.width),
                        huge(base.height) ? base.height : px(base.height)));
            }
            Object size = c.getClientProperty(SIZE);
            if (size instanceof Dimension) {
                Dimension base = (Dimension) size;
                c.setPreferredSize(new Dimension(px(base.width), px(base.height)));
            }
            Object min = c.getClientProperty(MIN);
            if (min instanceof Dimension) {
                Dimension base = (Dimension) min;
                c.setMinimumSize(new Dimension(px(base.width), px(base.height)));
            }
            Object font = c.getClientProperty(FONT);
            if (font instanceof Font) {
                c.setFont(scaled((Font) font));
            }
            Object row = c.getClientProperty(ROW);
            if (row instanceof Integer) {
                setRow(c, px((Integer) row));
            }
        }
        if (component instanceof Container) {
            for (Component child : ((Container) component).getComponents()) {
                reapply(child);
            }
        }
        // Renderers draw the rows of lists, tables and combo boxes, but aren't in the tree.
        if (component instanceof JList && ((JList<?>) component).getCellRenderer() instanceof Component) {
            reapply((Component) ((JList<?>) component).getCellRenderer());
        } else if (component instanceof JList && ((JList<?>) component).getCellRenderer() instanceof Aware) {
            ((Aware) ((JList<?>) component).getCellRenderer()).zoomChanged();
        }
        if (component instanceof javax.swing.JComboBox && ((javax.swing.JComboBox<?>) component).getRenderer() instanceof Component) {
            reapply((Component) ((javax.swing.JComboBox<?>) component).getRenderer());
        }
        if (component instanceof JTable) {
            for (Class<?> type : new Class<?>[] {Object.class, Number.class, Boolean.class}) {
                if (((JTable) component).getDefaultRenderer(type) instanceof Component) {
                    reapply((Component) ((JTable) component).getDefaultRenderer(type));
                }
            }
        }
        if (component instanceof javax.swing.JMenu) {
            for (Component item : ((javax.swing.JMenu) component).getMenuComponents()) {
                reapply(item);
            }
        }
        if (component instanceof Aware) { // after its children have their new fonts, which it may measure
            ((Aware) component).zoomChanged();
        }
    }
}
