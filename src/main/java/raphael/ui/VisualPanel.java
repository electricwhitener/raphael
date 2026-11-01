package raphael.ui;

import java.awt.Container;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.event.HierarchyBoundsAdapter;
import java.awt.event.HierarchyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JViewport;
import javax.swing.SwingUtilities;
import raphael.model.Snapshot;
import raphael.render.Animator;
import raphael.render.Scene;
import raphael.render.ScenePainter;
import raphael.render.Style;

// The picture of the current step: the program's arrays, lists, trees and objects.
// Moving to another step animates the change (see Animator). The picture is centred, and scaled
// to fit the panel, using one scale and offset for the whole run so it never jumps between steps.
// Ctrl + mouse wheel zooms on top of that, keeping the point under the mouse in place.
// A red message at the top says if the program crashed at this step, needs input or didn't compile.
// OOP: inheritance (extends JPanel) and overriding (paintComponent: Swing calls it whenever the
// panel needs drawing); implements TraceListener (Observer pattern).
public class VisualPanel extends JPanel implements TraceListener {

    private static final double MIN_ZOOM = 0.5;
    private static final double MAX_ZOOM = 3;
    private static final double MIN_FIT = 0.6;
    private static final double MAX_FIT = 1.5;

    private final TraceModel model;
    private final ScenePainter painter = new ScenePainter();
    private final Animator animator = new Animator(this::repaint);
    private int shownStep = -1;
    private double fit = 1;  // the scale that fits this run's biggest picture into the panel
    private double zoom = 1; // the user's zoom, on top of that
    private boolean recording;
    private String messageTitle; // a red message from the window, or null
    private String messageDetail;
    private Dimension size = new Dimension(400, 300);

    public VisualPanel(TraceModel model) {
        this.model = model;
        setBackground(Style.BACKGROUND);
        setFocusable(true);
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                requestFocusInWindow(); // so the arrow keys step the program
            }
        });
        addMouseWheelListener(e -> {
            if (e.isControlDown()) {
                zoomBy(Math.pow(1.1, -e.getPreciseWheelRotation()), e.getPoint());
            } else { // let the scroll pane scroll as usual
                getParent().dispatchEvent(SwingUtilities.convertMouseEvent(this, e, getParent()));
            }
        });
        addHierarchyBoundsListener(new HierarchyBoundsAdapter() {
            @Override
            public void ancestorResized(HierarchyEvent e) {
                fitToRun(); // the window or a divider moved, so there's a different amount of room
            }
        });
    }

    @Override
    public void traceChanged(TraceModel model) {
        setMessage(null, null);
        animator.show(model.getScene()); // a new run starts without animating from the old one
        shownStep = model.getStep();
        fitToRun();
    }

    @Override
    public void stepChanged(TraceModel model) {
        int moved = model.getStep() - shownStep;
        if (Math.abs(moved) > 1) {
            animator.jumpTo(model.getScene(), model.getSpeed());
        } else {
            animator.animateTo(model.getScene(), model.getSpeed(), moved == 1);
        }
        shownStep = model.getStep();
        updateSize();
    }

    // Chooses the one scale for this run: as big as fits the room around the panel, within limits.
    private void fitToRun() {
        Dimension run = model.getRunSize();
        Container room = SwingUtilities.getAncestorOfClass(JScrollPane.class, this);
        fit = 1;
        if (room != null && run.width > 0 && run.height > 0) {
            double best = Math.min((room.getWidth() - 2.0) / run.width, (room.getHeight() - 2.0) / run.height);
            fit = Math.max(MIN_FIT, Math.min(MAX_FIT, best));
        }
        updateSize();
    }

    // Only lays the panel out again when the picture's size really changes (not every frame).
    private void updateSize() {
        Dimension run = model.getRunSize();
        Dimension scene = run.width > 0 ? run : new Dimension(400, 300);
        Dimension wanted = new Dimension((int) (scene.width * fit * zoom), (int) (scene.height * fit * zoom));
        if (!wanted.equals(size)) {
            size = wanted;
            revalidate();
        }
        repaint();
    }

    @Override
    public Dimension getPreferredSize() {
        return size;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g); // fills the background
        ((Graphics2D) g).setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        Graphics2D g2 = (Graphics2D) g.create();
        Scene scene = animator.getCurrent();
        if (messageTitle != null && !model.hasSteps()) {
            // the message says it all
        } else if (scene == null || !model.hasSteps()) {
            hint(g2, recording ? "recording your program…" : "press run (ctrl+enter) to trace the program");
        } else {
            if (scene.getNodes().isEmpty()) {
                hint(g2, "no arrays or objects yet · plain variables are listed below");
            }
            Dimension run = model.getRunSize();
            double scale = fit * zoom;
            // Whole pixels, so thin lines stay as sharp as the scale allows.
            g2.translate(Math.max(0, Math.round((getWidth() - run.width * scale) / 2)),
                    Math.max(0, Math.round((getHeight() - run.height * scale) / 2)));
            g2.scale(scale, scale);
            painter.paint(g2, scene);
        }
        g2.dispose();
        Snapshot snapshot = model.getSnapshot();
        if (messageTitle != null) {
            banner((Graphics2D) g, messageTitle, messageDetail);
        } else if (snapshot != null && snapshot.getException() != null) {
            banner((Graphics2D) g, "crashed here:", snapshot.getException());
        }
        if (zoom != 1) {
            badge((Graphics2D) g, Math.round(zoom * 100) + "%");
        }
        Toolkit.getDefaultToolkit().sync(); // show this frame now (stops stutter on Linux)
    }

    private void zoomBy(double factor, Point mouse) {
        double old = zoom;
        zoom = factor == 0 ? 1 : Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, zoom * factor));
        if (zoom == old) {
            return;
        }
        JViewport viewport = (JViewport) SwingUtilities.getAncestorOfClass(JViewport.class, this);
        Point view = viewport == null ? new Point() : viewport.getViewPosition();
        Point anchor = mouse != null ? mouse : new Point(view.x + 40, view.y + 40);
        updateSize();
        if (viewport != null) { // keep the point under the mouse where it was
            int x = (int) (anchor.x / old * zoom - (anchor.x - view.x));
            int y = (int) (anchor.y / old * zoom - (anchor.y - view.y));
            SwingUtilities.invokeLater(() -> viewport.setViewPosition(new Point(Math.max(0, x), Math.max(0, y))));
        }
    }

    public void resetZoom() {
        zoomBy(0, null);
    }

    // A red message at the top of the picture, or null for none.
    public void setMessage(String title, String detail) {
        messageTitle = title;
        messageDetail = detail;
        repaint();
    }

    // While a program is being recorded, the empty picture says so.
    public void setRecording(boolean recording) {
        this.recording = recording;
        repaint();
    }

    private void hint(Graphics2D g, String text) {
        g.setFont(Style.UI_FONT);
        g.setColor(Style.MUTED);
        FontMetrics metrics = g.getFontMetrics();
        Rectangle visible = getVisibleRect();
        g.drawString(text, visible.x + (visible.width - metrics.stringWidth(text)) / 2, visible.y + visible.height / 2);
    }

    // A title, then the details wrapped to fit, on a plain band so they can be read over anything.
    private void banner(Graphics2D g, String title, String detail) {
        g.setFont(Style.LABEL_FONT);
        FontMetrics metrics = g.getFontMetrics();
        Rectangle visible = getVisibleRect();
        List<String> lines = new ArrayList<>();
        lines.add(title);
        for (String line : detail.split("\n")) {
            String current = "";
            for (String word : line.split(" ")) {
                String longer = current.isEmpty() ? word : current + " " + word;
                if (metrics.stringWidth(longer) > visible.width - 2 * Style.MARGIN && !current.isEmpty()) {
                    lines.add(current);
                    current = word;
                } else {
                    current = longer;
                }
            }
            lines.add(current);
        }
        g.setColor(Style.BACKGROUND);
        g.fillRect(visible.x, visible.y, visible.width, lines.size() * 18 + 12);
        g.setColor(Style.ERROR);
        for (int i = 0; i < lines.size(); i++) {
            g.drawString(lines.get(i), visible.x + Style.MARGIN, visible.y + 18 + i * 18);
        }
    }

    private void badge(Graphics2D g, String text) {
        g.setFont(Style.CAPTION_FONT);
        g.setColor(Style.MUTED);
        Rectangle visible = getVisibleRect();
        g.drawString(text, visible.x + visible.width - g.getFontMetrics().stringWidth(text) - 10, visible.y + 16);
    }
}
