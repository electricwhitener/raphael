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
import raphael.explain.ErrorExplainer;
import raphael.model.ThrownException;
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
    private int recordedSteps; // shown while recording
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
            if (Shortcuts.isDown(e)) {
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
            if (recording) {
                recordingCard(g2);
            } else {
                hint(g2, Shortcuts.label("press run (ctrl+enter) to trace the program"));
            }
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
            String crash = snapshot.getException();
            int colon = crash.indexOf(": ");
            String explanation = colon < 0 ? ErrorExplainer.explain(crash, null)
                    : ErrorExplainer.explain(crash.substring(0, colon), crash.substring(colon + 2));
            banner((Graphics2D) g, "crashed here:", crash + "\n\n" + explanation);
        } else if (snapshot != null && snapshot.getThrown() != null) {
            caughtCard((Graphics2D) g, snapshot.getThrown());
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
        this.recordedSteps = 0;
        repaint();
    }

    // "Recording… 1,284 steps", and how to stop, in the middle of the picture area.
    private void recordingCard(Graphics2D g) {
        Rectangle visible = getVisibleRect();
        String title = recordedSteps == 0 ? "Recording…" : String.format(java.util.Locale.ENGLISH, "Recording…  %,d steps", recordedSteps);
        String detail = "Press Esc or Stop to end the recording here.";
        g.setRenderingHint(java.awt.RenderingHints.KEY_TEXT_ANTIALIASING, java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setFont(Style.sans(true, 16f));
        g.setColor(Style.INK);
        int middle = visible.y + visible.height / 2;
        g.drawString(title, visible.x + (visible.width - g.getFontMetrics().stringWidth(title)) / 2, middle - 6);
        g.setFont(Style.sans(false, 13f));
        g.setColor(Style.INK_SOFT);
        g.drawString(detail, visible.x + (visible.width - g.getFontMetrics().stringWidth(detail)) / 2, middle + 18);
    }

    public void setRecordedSteps(int steps) {
        if (steps != recordedSteps) {
            recordedSteps = steps;
            repaint();
        }
    }

    public boolean hasMessage() {
        return messageTitle != null;
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

    // A caught exception, on the first step of its catch: an outlined card at the bottom of the
    // picture (the drawing is usually centred, so it rarely covers anything) that says what was
    // thrown, where it was thrown and caught, and what it means. Red outline, not filled: the
    // program handled it, so it isn't a crash.
    private void caughtCard(Graphics2D g, ThrownException thrown) {
        g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(java.awt.RenderingHints.KEY_TEXT_ANTIALIASING, java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        Rectangle visible = getVisibleRect();
        int width = Math.min(560, visible.width - 2 * Style.SPACE_6);
        int inner = width - 2 * Style.SPACE_3;
        java.awt.Font title = Style.sans(true, 13f);
        java.awt.Font body = Style.sans(false, 12.5f);
        String where = (thrown.throwLine() > 0 ? "thrown at line " + thrown.throwLine() + " · " : "")
                + "caught at line " + thrown.catchLine()
                + (thrown.leftMethods().isEmpty() ? "" : " · left " + String.join("(), ", thrown.leftMethods()) + "()");
        List<String> what = wrap(thrown.text(), g.getFontMetrics(Style.UI_FONT.deriveFont(12f)), inner);
        // Advice for this kind of exception if there is some; otherwise what the catch just did.
        String meaning = ErrorExplainer.hasAdvice(thrown.type()) ? ErrorExplainer.explain(thrown.type(), thrown.message())
                : "The rest of the try was skipped" + (thrown.leftMethods().isEmpty() ? ""
                        : ", and " + String.join("(), ", thrown.leftMethods()) + "() stopped where "
                        + (thrown.leftMethods().size() == 1 ? "it was" : "they were"))
                        + ". The program goes on in this catch.";
        List<String> explanation = wrap(meaning, g.getFontMetrics(body), inner);
        int line = 18;
        int height = Style.SPACE_3 * 2 + line * (2 + what.size() + explanation.size()) + Style.SPACE_1;
        int x = visible.x + Style.SPACE_6;
        int y = visible.y + visible.height - height - Style.SPACE_3;
        g.setColor(Style.BACKGROUND);
        g.fillRoundRect(x, y, width, height, Style.ARC, Style.ARC);
        g.setColor(Style.ERROR);
        g.setStroke(new java.awt.BasicStroke(1.5f));
        g.drawRoundRect(x, y, width, height, Style.ARC, Style.ARC);
        int baseline = y + Style.SPACE_3 + 13;
        g.setFont(title);
        g.drawString("caught an exception", x + Style.SPACE_3, baseline);
        g.setFont(Style.UI_FONT.deriveFont(12f));
        g.setColor(Style.INK);
        for (String part : what) {
            baseline += line;
            g.drawString(part, x + Style.SPACE_3, baseline);
        }
        g.setFont(body);
        g.setColor(Style.MUTED);
        baseline += line;
        g.drawString(where, x + Style.SPACE_3, baseline);
        g.setColor(Style.INK_SOFT);
        baseline += Style.SPACE_1;
        for (String part : explanation) {
            baseline += line;
            g.drawString(part, x + Style.SPACE_3, baseline);
        }
    }

    // Splits text into lines no wider than width, at spaces.
    private static List<String> wrap(String text, FontMetrics metrics, int width) {
        List<String> lines = new ArrayList<>();
        for (String paragraph : text.split("\n")) {
            String current = "";
            for (String word : paragraph.split(" ")) {
                String longer = current.isEmpty() ? word : current + " " + word;
                if (metrics.stringWidth(longer) > width && !current.isEmpty()) {
                    lines.add(current);
                    current = word;
                } else {
                    current = longer;
                }
            }
            lines.add(current);
        }
        return lines;
    }

    private void badge(Graphics2D g, String text) {
        g.setFont(Style.CAPTION_FONT);
        g.setColor(Style.MUTED);
        Rectangle visible = getVisibleRect();
        g.drawString(text, visible.x + visible.width - g.getFontMetrics().stringWidth(text) - 10, visible.y + 16);
    }
}
