package raphael.ui;

import java.awt.BasicStroke;
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
import raphael.model.Trace;
import raphael.render.Style;

// The picture of the current step: the program's arrays, lists, trees and objects.
// Moving to another step animates the change (see Animator). The picture is centred, and scaled
// to fit the panel, using one scale and offset for the whole run so it never jumps between steps.
// Ctrl + mouse wheel zooms on top of that, keeping the point under the mouse in place.
// A red message at the top says if the program crashed at this step, needs input or didn't compile.
// OOP: inheritance (extends JPanel) and overriding (paintComponent: Swing calls it whenever the
// panel needs drawing); implements TraceListener (Observer pattern).
public class VisualPanel extends JPanel implements TraceListener, Zoom.Aware {

    private static final double MIN_ZOOM = 0.5;
    private static final double MAX_ZOOM = 3;

    private final TraceModel model;
    private final ScenePainter painter = new ScenePainter();
    private final Animator animator = new Animator(this::repaint);
    // The calls view of the same run (the call stack as boxes), shown instead of the picture when
    // chosen; see showCalls().
    private final java.util.Map<View, raphael.render.SceneSource> views = new java.util.EnumMap<>(View.class); // all but the picture
    // Which view of the run is on show: the picture of the data, the calls, or the objects.
    // The views, each with how far it may be scaled to fit (see Style.FIT_*).
    public enum View {
        PICTURE(Style.FIT_PICTURE_MIN, Style.FIT_PICTURE_MAX, "no arrays or objects yet · plain variables are listed below"),
        CALLS(Style.FIT_CALLS_MIN, Style.FIT_CALLS_MAX, "no calls yet"),
        OBJECTS(Style.FIT_OBJECTS_MIN, Style.FIT_OBJECTS_MAX, "no objects yet · they appear when the program says new"),
        FILES(Style.FIT_FILES_MIN, Style.FIT_FILES_MAX, "no input or files yet");

        private final double minFit;
        private final double maxFit;
        private final String empty;

        View(double minFit, double maxFit, String empty) {
            this.minFit = minFit;
            this.maxFit = maxFit;
            this.empty = empty;
        }
    }
    private View view = View.PICTURE;
    private int shownStep = -1;
    private double fit = 1;  // the scale that fits this run's biggest picture into the panel
    private double zoom = 1; // the user's zoom, on top of that
    private boolean recording;
    private int recordedSteps; // shown while recording
    private boolean waitingForInput; // the program being recorded waits for the user to type
    private Note message;        // a message from the window (a compile error…), or null
    private int reserve;         // room kept at the bottom, for the whole run, for its tallest card
    private boolean compact;     // cards leave out their explanation, so the reserve stays small
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
        views.clear();
        if (model.getTrace() != null) {
            views.put(View.CALLS, new raphael.render.CallsScenes(model.getTrace(), model.getAllMoments()));
            views.put(View.OBJECTS, new raphael.render.ObjectsScenes(model.getTrace(), model.getFacts()));
            views.put(View.FILES, new raphael.render.FilesScenes(model.getTrace(), model.getFacts(), model.getInput()));
        }
        clearMessage();
        animator.show(sceneNow()); // a new run starts without animating from the old one
        shownStep = model.getStep();
        fitToRun();
    }

    @Override
    public void stepChanged(TraceModel model) {
        int moved = model.getStep() - shownStep;
        if (Math.abs(moved) > 1) {
            animator.jumpTo(sceneNow(), model.getSpeed());
        } else {
            animator.animateTo(sceneNow(), model.getSpeed(), moved == 1);
        }
        shownStep = model.getStep();
        updateSize();
        showFocus();
    }

    // In a view that's bigger than the room (a wide tree of calls), keeps the running call or
    // object in sight: the shape outlined as "running" is scrolled into view.
    private void showFocus() {
        if (view == View.PICTURE) {
            return;
        }
        Scene scene = sceneNow();
        if (scene == null) {
            return;
        }
        for (raphael.render.SceneNode node : scene.getNodes()) {
            if (node.kind() == raphael.render.SceneNode.Kind.FOCUS_WRITE) {
                Dimension run = runSize();
                double scale = fit * zoom;
                double left = Math.max(0, Math.round((getWidth() - run.width * scale) / 2));
                double top = Math.max(0, Math.round((getHeight() - reserve - run.height * scale) / 2));
                int pad = 40;
                java.awt.Rectangle box = new java.awt.Rectangle((int) (left + node.x() * scale) - pad, (int) (top + node.y() * scale) - pad,
                        (int) (node.w() * scale) + 2 * pad, (int) (node.h() * scale) + 2 * pad);
                SwingUtilities.invokeLater(() -> scrollRectToVisible(box)); // after the panel has its new size
                return;
            }
        }
    }

    // Whether a view has something to show for this run (the picture always does).
    public boolean hasContent(View chosen) {
        if (chosen == View.OBJECTS) {
            return views.get(chosen) instanceof raphael.render.ObjectsScenes && ((raphael.render.ObjectsScenes) views.get(chosen)).hasClasses();
        }
        if (chosen == View.FILES) {
            return views.get(chosen) instanceof raphael.render.FilesScenes && ((raphael.render.FilesScenes) views.get(chosen)).hasContent();
        }
        if (chosen == View.CALLS) {
            if (model.getTrace() == null) {
                return false;
            }
            for (raphael.model.Snapshot step : model.getTrace().getSnapshots()) {
                if (step.getFrames().size() > 1) {
                    return true; // main called a method of its own
                }
            }
            return false;
        }
        return true;
    }

    // The scene for the step on show: the picture of the data, or the calls view.
    private Scene sceneNow() {
        if (!model.hasSteps()) {
            return model.getScene();
        }
        raphael.render.SceneSource source = views.get(view);
        return source == null ? model.getScene() : source.sceneAt(model.getStep());
    }

    private Dimension runSize() {
        raphael.render.SceneSource source = views.get(view);
        return source == null ? model.getRunSize() : source.getRunSize();
    }

    // Shows one view of the run, for this run and the next ones.
    public void showView(View chosen) {
        if (chosen == view) {
            return;
        }
        view = chosen;
        animator.show(sceneNow());
        fitToRun();
        showFocus();
        revalidate();
        repaint();
    }

    public View getView() {
        return view;
    }

    // The interface's zoom changed: the picture and its cards fit again.
    @Override
    public void zoomChanged() {
        fitToRun();
    }

    // Chooses the one scale for this run: as big as fits the room around the panel, within limits.
    private void fitToRun() {
        Dimension run = runSize();
        Container room = SwingUtilities.getAncestorOfClass(JScrollPane.class, this);
        fit = 1;
        reserve = 0;
        compact = false;
        if (room != null && run.width > 0 && run.height > 0) {
            // The cards (a caught exception, a crash) go in a strip at the bottom, kept for the
            // whole run, so the drawing never jumps and never hides under one.
            // The cards are drawn at the interface's zoom (overlays()), so they're measured in its units.
            double z = Zoom.factor();
            int width = cardWidth((int) (room.getWidth() / z));
            reserve = (int) Math.ceil(tallestCard(width, false) * z);
            if (reserve > room.getHeight() * 0.4) { // a short window: shorter cards
                compact = true;
                reserve = (int) Math.ceil(tallestCard(width, true) * z);
            }
            double best = Math.min((room.getWidth() - 2.0) / run.width, (room.getHeight() - 2.0 - reserve) / run.height);
            // The picture grows with the interface's zoom too, as far as the room allows.
            fit = Math.max(view.minFit * z, Math.min(view.maxFit * z, best));
        }
        updateSize();
    }

    // Only lays the panel out again when the picture's size really changes (not every frame).
    private void updateSize() {
        Dimension run = runSize();
        Dimension scene = run.width > 0 ? run : new Dimension(400, 300);
        Dimension wanted = new Dimension((int) (scene.width * fit * zoom), (int) (scene.height * fit * zoom) + reserve);
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
        if (message != null && !model.hasSteps()) {
            // the message says it all
        } else if (scene == null || !model.hasSteps()) {
            Graphics2D zoomed = overlays(g);
            if (recording) {
                recordingCard(zoomed);
            } else {
                hint(zoomed, Shortcuts.label("press run (ctrl+enter) to trace the program"));
            }
            zoomed.dispose();
        } else {
            if (scene.getNodes().isEmpty()) {
                Graphics2D zoomed = overlays(g);
                hint(zoomed, view.empty);
                zoomed.dispose();
            }
            Dimension run = runSize();
            double scale = fit * zoom;
            // Whole pixels, so thin lines stay as sharp as the scale allows.
            g2.translate(Math.max(0, Math.round((getWidth() - run.width * scale) / 2)),
                    Math.max(0, Math.round((getHeight() - reserve - run.height * scale) / 2)));
            g2.scale(scale, scale);
            painter.paint(g2, scene);
        }
        g2.dispose();
        Note card = model.hasSteps() ? cardFor(model.getStep()) : null;
        Graphics2D zoomed = overlays(g);
        if (card != null) {
            note(zoomed, card);
        }
        if (message != null) {
            note(zoomed, message);
        }
        if (zoom != 1) {
            badge(zoomed, Math.round(zoom * 100) + "%");
        }
        zoomed.dispose();
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

    // A message card at the top of the picture: a title ("didn't compile · 2 errors"), the
    // headline, an optional line of facts, and an optional explanation or hint.
    public void setMessage(String title, String headline, String facts, String explanation) {
        message = new Note(title, headline, facts, explanation, true, false);
        repaint();
    }

    public void clearMessage() {
        message = null;
        repaint();
    }

    // While a program is being recorded, the empty picture says so.
    public void setRecording(boolean recording) {
        this.recording = recording;
        this.recordedSteps = 0;
        this.waitingForInput = false;
        repaint();
    }

    public void setWaitingForInput(boolean waiting) {
        if (waiting != waitingForInput) {
            waitingForInput = waiting;
            repaint();
        }
    }

    // "Recording… 1,284 steps", and how to stop, in the middle of the picture area.
    private void recordingCard(Graphics2D g) {
        Rectangle visible = zoomedVisible();
        String title = waitingForInput ? "Waiting for input"
                : recordedSteps == 0 ? "Recording…" : String.format(java.util.Locale.ENGLISH, "Recording…  %,d steps", recordedSteps);
        String detail = waitingForInput ? "Type it in the Terminal tab and press Enter."
                : "Press Esc or Stop to end the recording here.";
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
        return message != null;
    }

    // The visible area in the overlays' units: they draw at 100% sizes on a Graphics scaled by the
    // interface's zoom (see overlays()), so their text and boxes grow with it.
    private Rectangle zoomedVisible() {
        Rectangle visible = getVisibleRect();
        double z = Zoom.factor();
        return new Rectangle((int) (visible.x / z), (int) (visible.y / z), (int) (visible.width / z), (int) (visible.height / z));
    }

    private static Graphics2D overlays(Graphics g) {
        Graphics2D zoomed = (Graphics2D) g.create();
        zoomed.scale(Zoom.factor(), Zoom.factor());
        return zoomed;
    }

    private void hint(Graphics2D g, String text) {
        g.setFont(Style.UI_FONT);
        g.setColor(Style.MUTED);
        FontMetrics metrics = g.getFontMetrics();
        Rectangle visible = zoomedVisible();
        g.drawString(text, visible.x + (visible.width - metrics.stringWidth(text)) / 2, visible.y + visible.height / 2);
    }

    // A title, then the details wrapped to fit, on a plain band so they can be read over anything.
    // A card in the picture: a red title, a headline in the code font, a line of facts, and an
    // explanation, which is the most useful line and so the strongest text. Three kinds share it:
    //   a caught exception: outlined (the program handled it), at the bottom;
    //   a crash: filled with the error tint, at the bottom;
    //   a message from the window (a compile error, missing input): filled, near the top.
    // OOP: a record, a small unchangeable data class.
    //   a key moment (explain/Moment): outlined in the accent blue, at the bottom.
    private record Note(String title, String headline, String facts, String explanation, boolean filled, boolean atBottom,
                        boolean moment) {

        Note(String title, String headline, String facts, String explanation, boolean filled, boolean atBottom) {
            this(title, headline, facts, explanation, filled, atBottom, false);
        }
    }

    // One line of a card, with its kind: title, headline, facts or body.
    private record NoteLine(String text, String kind) {
    }

    private static final float BODY_SIZE = 12.5f;

    // The card for this step, if it has one: a crash, a caught exception (explained by its key
    // moment, which says which catch took it and why), or the step's other key moments.
    private Note cardFor(int step) {
        Trace trace = model.getTrace();
        if (trace == null || step < 0 || step >= trace.size()) {
            return null;
        }
        Snapshot snapshot = trace.get(step);
        List<raphael.explain.Moment> moments = model.momentsAt(step);
        if (snapshot.getException() != null) {
            String crash = snapshot.getException();
            int colon = crash.indexOf(": ");
            String explanation = colon < 0 ? ErrorExplainer.explain(crash, null)
                    : ErrorExplainer.explain(crash.substring(0, colon), crash.substring(colon + 2));
            return new Note("crashed at line " + snapshot.getLine(), crash, null, explanation, true, true);
        }
        ThrownException thrown = snapshot.getThrown();
        if (thrown != null) {
            List<String> left = thrown.leftMethods();
            String where = (thrown.throwLine() > 0 ? "thrown at line " + thrown.throwLine() + " · " : "")
                    + "caught at line " + thrown.catchLine()
                    + (left.isEmpty() ? "" : " · left " + String.join("(), ", left) + "()");
            String said = null;
            for (raphael.explain.Moment moment : moments) {
                if (moment.kind().equals("caught") || moment.kind().equals("file-missing")) {
                    said = moment.text();
                }
            }
            String advice = ErrorExplainer.hasAdvice(thrown.type()) ? ErrorExplainer.explain(thrown.type(), thrown.message()) : null;
            String meaning = said != null ? said + (advice != null ? "\n" + advice : "")
                    : advice != null ? advice
                    : "The rest of the try was skipped" + (left.isEmpty() ? ""
                            : ", and " + String.join("(), ", left) + "() stopped where " + (left.size() == 1 ? "it was" : "they were"))
                            + ". The program goes on in this catch.";
            return new Note("caught an exception", thrown.text(), where, meaning, false, true);
        }
        if (moments.isEmpty()) {
            return null;
        }
        List<String> texts = new ArrayList<>();
        for (raphael.explain.Moment moment : moments) {
            texts.add(moment.text());
        }
        String title = moments.get(0).title() + (moments.size() > 1 ? "  ·  +" + (moments.size() - 1) + " more" : "");
        return new Note(title, null, null, String.join("\n", texts), false, true, true);
    }

    private static int cardWidth(int roomWidth) {
        return Math.max(200, Math.min(Style.NOTE_MAX_W, roomWidth - 2 * Style.SPACE_6));
    }

    // The room the run's tallest bottom card needs, gaps included, or 0 if the run has none.
    private int tallestCard(int width, boolean shortened) {
        Trace trace = model.getTrace();
        int tallest = 0;
        if (trace != null) {
            for (int step = 0; step < trace.size(); step++) {
                Note card = cardFor(step);
                if (card != null) {
                    tallest = Math.max(tallest, cardHeight(lines(card, width, shortened)));
                }
            }
        }
        return tallest == 0 ? 0 : tallest + 2 * Style.NOTE_GAP;
    }

    // The card's lines, wrapped to fit inside a card this wide. A shortened card leaves out the
    // explanation, for windows too short to keep room for it.
    private List<NoteLine> lines(Note card, int width, boolean shortened) {
        int inside = width - 2 * Style.SPACE_3;
        FontMetrics body = getFontMetrics(Style.sans(false, BODY_SIZE));
        List<NoteLine> lines = new ArrayList<>();
        lines.add(new NoteLine(card.title(), "title"));
        if (card.headline() != null) {
            for (String line : wrap(card.headline(), getFontMetrics(Style.UI_FONT.deriveFont(12f)), inside)) {
                lines.add(new NoteLine(line, "headline"));
            }
        }
        if (card.facts() != null) {
            for (String line : wrap(card.facts(), body, inside)) {
                lines.add(new NoteLine(line, "facts"));
            }
        }
        if (card.explanation() != null && shortened && card.moment()) {
            // Too little room for the whole sentence, but a moment without its sentence says nothing:
            // its first line stays, shortened.
            List<String> all = wrap(card.explanation(), body, inside);
            String first = all.get(0);
            if (all.size() > 1) {
                while (!first.isEmpty() && body.stringWidth(first + " …") > inside) {
                    first = first.substring(0, first.length() - 1);
                }
                first = first.stripTrailing() + " …";
            }
            lines.add(new NoteLine(first, "body"));
        }
        if (card.explanation() != null && !shortened) {
            for (String line : wrap(card.explanation(), body, inside)) {
                lines.add(new NoteLine(line, "body"));
            }
        }
        return lines;
    }

    private static int cardHeight(List<NoteLine> lines) {
        boolean hasBody = lines.stream().anyMatch(line -> line.kind().equals("body"));
        return 2 * Style.SPACE_3 + lines.size() * Style.NOTE_LINE_H + (hasBody ? Style.SPACE_1 : 0) - 4;
    }

    private void note(Graphics2D g, Note card) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        Rectangle visible = zoomedVisible();
        int width = cardWidth(visible.width);
        List<NoteLine> lines = lines(card, width, card.atBottom() && compact);
        int height = cardHeight(lines);
        int x = visible.x + (visible.width - width) / 2;
        int y = card.atBottom() ? visible.y + visible.height - height - Style.NOTE_GAP : visible.y + Style.SPACE_6;
        g.setColor(card.filled() ? Style.ERROR_TINT : Style.BACKGROUND);
        g.fillRoundRect(x, y, width, height, Style.ARC, Style.ARC);
        g.setColor(card.moment() ? Style.ACCENT : Style.ERROR);
        g.setStroke(new BasicStroke(card.filled() ? 1f : 1.5f));
        g.drawRoundRect(x, y, width, height, Style.ARC, Style.ARC);
        int baseline = y + Style.SPACE_3 + 10;
        String before = "";
        for (NoteLine line : lines) {
            switch (line.kind()) {
                case "title" -> {
                    g.setFont(Style.sans(true, 13f));
                    g.setColor(card.moment() ? Style.ACCENT : Style.ERROR);
                }
                case "headline" -> {
                    g.setFont(Style.UI_FONT.deriveFont(12f));
                    g.setColor(Style.INK);
                }
                case "facts" -> {
                    g.setFont(Style.sans(false, BODY_SIZE));
                    g.setColor(Style.MUTED);
                }
                default -> {
                    g.setFont(Style.sans(false, BODY_SIZE));
                    g.setColor(Style.INK);
                    if (!before.equals("body")) {
                        baseline += Style.SPACE_1; // a little room before the explanation
                    }
                }
            }
            g.drawString(line.text(), x + Style.SPACE_3, baseline);
            baseline += Style.NOTE_LINE_H;
            before = line.kind();
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
        Rectangle visible = zoomedVisible();
        g.drawString(text, visible.x + visible.width - g.getFontMetrics().stringWidth(text) - 10, visible.y + 16);
    }
}
