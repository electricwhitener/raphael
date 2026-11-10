package raphael.render;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.font.GlyphVector;
import java.awt.font.LineMetrics;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.util.LinkedHashMap;
import java.util.Map;

// Draws a Scene with Java 2D (Graphics2D): arrows first, then the shapes on top of them, then
// the start of each reference arrow (Java 2D paints in order, so a slot would cover its dot).
// (0, 0) is the top-left corner and y grows downwards.
// Two details keep movement smooth: shapes are drawn at exact fractional positions
// (STROKE_PURE: nothing is snapped to whole pixels), and text is drawn as glyph outlines,
// because drawString() rounds each word to a whole pixel and would make it wobble inside a
// moving box. Every arrow is a Bézier curve, so a straight arrow can bend smoothly into a
// curved one while it swings to a new target.
// OOP: encapsulation. paint() is the only public method; every drawing detail is private.
public class ScenePainter {

    private static final BasicStroke HAIR = new BasicStroke(1f);
    private static final BasicStroke LINE = new BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
    private static final BasicStroke FOCUS = new BasicStroke(2f);
    private static final BasicStroke DASHED = new BasicStroke(1.4f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
            10f, new float[] {4f, 3f}, 0f);

    // Making a glyph outline is slow, so each piece of text is made once and reused.
    private final Map<String, GlyphVector> glyphs = new LinkedHashMap<>(256, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, GlyphVector> eldest) {
            return size() > 1000;
        }
    };

    public void paint(Graphics2D g, Scene scene) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        for (SceneEdge edge : scene.getEdges()) {
            paintEdge(g, scene, edge);
        }
        for (SceneNode node : scene.getNodes()) {
            paintNode(g, node);
        }
        for (SceneEdge edge : scene.getEdges()) {
            paintArrowStart(g, scene, edge);
        }
        g.setComposite(AlphaComposite.SrcOver);
    }

    // ---- arrows and lines ----

    private void paintEdge(Graphics2D g, Scene scene, SceneEdge edge) {
        Curve curve = curveOf(scene, edge);
        double alpha = alphaOf(scene, edge);
        if (curve == null || alpha <= 0.003) {
            return;
        }
        double reach = reachOf(edge);
        Color color = edge.kind() == SceneEdge.Kind.POINTER ? Style.ACCENT : Style.INK_SOFT;
        setAlpha(g, alpha);
        g.setColor(color);
        boolean uml = edge.kind() == SceneEdge.Kind.EXTENDS || edge.kind() == SceneEdge.Kind.IMPLEMENTS;
        g.setStroke(edge.kind() == SceneEdge.Kind.IMPLEMENTS ? DASHED : LINE);
        g.draw(curve.path());
        if (uml) {
            hollowHead(g, curve);
        } else if (edge.kind() != SceneEdge.Kind.TREE) {
            arrowHead(g, curve, reach);
        }
    }

    // The dot in a pointer slot, and the part of the arrow inside the slot, on top of the slot.
    private void paintArrowStart(Graphics2D g, Scene scene, SceneEdge edge) {
        if (edge.kind() != SceneEdge.Kind.ARROW && edge.kind() != SceneEdge.Kind.PREV_ARROW) {
            return;
        }
        SceneNode from = scene.getNode(edge.from());
        Curve curve = curveOf(scene, edge);
        double alpha = alphaOf(scene, edge);
        if (curve == null || from.kind() != SceneNode.Kind.SLOT || alpha <= 0.003) {
            return;
        }
        g.setColor(Style.INK_SOFT);
        g.setStroke(LINE);
        // An arrow from an array's cell can have to cross a box on its way. Showing it faintly on
        // top means it can't look like it stops at that box and starts again on the other side.
        if (from.caption() != null) {
            setAlpha(g, alpha * Style.ARROW_OVER_BOX);
            g.draw(curve.path());
        }
        setAlpha(g, alpha);
        Shape savedClip = g.getClip();
        g.clip(new Rectangle2D.Double(from.x(), from.y(), from.w(), from.h()));
        g.draw(curve.path());
        g.setClip(savedClip);
        setAlpha(g, alpha * Math.min(1, reachOf(edge) * 4));
        g.fill(new Ellipse2D.Double(curve.x0() - 2.6, curve.y0() - 2.6, 5.2, 5.2));
    }

    // Where this arrow runs at this moment, or null if it can't be drawn (e.g. its target is
    // beyond the object limit, or it has pulled all the way back into its slot).
    private Curve curveOf(Scene scene, SceneEdge edge) {
        SceneNode from = scene.getNode(edge.from());
        SceneNode to = scene.getNode(edge.to());
        double reach = reachOf(edge);
        if (from == null || to == null || reach <= 0.001) {
            return null;
        }
        SceneEdge.Look look = edge.look();
        Curve curve = route(from, to, edge.kind());
        if (look.oldTo() != null && scene.getNode(look.oldTo()) != null) {
            // Swinging: part of the way from the old route to the new one.
            curve = route(from, scene.getNode(look.oldTo()), edge.kind()).swing(curve, look.swing());
        }
        if (reach < 0.999) {
            curve = curve.upTo(reach); // growing out of its slot, or pulling back into it
        }
        return curve;
    }

    private static double reachOf(SceneEdge edge) {
        return Math.max(0, Math.min(1, edge.look().reach()));
    }

    // How visible an arrow is: its own look, and it fades with the shapes at its two ends.
    private static double alphaOf(Scene scene, SceneEdge edge) {
        SceneNode from = scene.getNode(edge.from());
        SceneNode to = scene.getNode(edge.to());
        return from == null || to == null ? 0 : edge.look().alpha() * Math.min(from.look().alpha(), to.look().alpha());
    }

    // The path an arrow takes from one shape to another. A reference pointing backwards along a
    // row (a reversed or circular list) curves underneath, so it doesn't cross the nodes between.
    // The bend fades in as the target moves left, so the arrow never jumps between the two shapes.
    private Curve route(SceneNode from, SceneNode to, SceneEdge.Kind kind) {
        if (kind == SceneEdge.Kind.POINTER) {
            double x = Math.max(to.x() + 6, Math.min(from.centerX(), to.x() + to.w() - 6));
            return Curve.line(x, from.bottom(), x, to.y());
        }
        if (kind == SceneEdge.Kind.EXTENDS || kind == SceneEdge.Kind.IMPLEMENTS) {
            // UML: from the middle of the child's top edge up to the middle of the parent's bottom
            return Curve.line(from.centerX(), from.y(), to.centerX(), to.bottom());
        }
        if (kind == SceneEdge.Kind.TREE) {
            Point2D a = border(from, to.centerX(), to.centerY());
            Point2D b = border(to, from.centerX(), from.centerY());
            return Curve.line(a.getX(), a.getY(), b.getX(), b.getY());
        }
        double shift = kind == SceneEdge.Kind.PREV_ARROW ? 6 : 0;
        double sx = from.centerX();
        double sy = from.centerY() + shift;
        boolean toArray = to.kind() == SceneNode.Kind.FRAME && to.caption() != null;
        if (toArray && to.bottom() <= from.y()) {
            // Arrays are drawn on top. An arrow up to one goes round the left side, so it can't
            // be mistaken for pointing at another array it would otherwise cross.
            double side = Math.min(to.x(), from.x()) - 40;
            double entry = to.bottom() - Style.CELL_H / 2.0;
            return new Curve(sx, sy, side, sy, side, entry, to.x(), entry);
        }
        Point2D end = toArray ? arrayEntry(to, sy) : border(to, sx, sy);
        Curve straight = Curve.line(sx, sy, end.getX(), end.getY() + shift);
        // A slot pointing at something below leaves through its own bottom edge, so the arrow
        // can't be mistaken for one that starts at the next cell along.
        double below = clamp((to.y() - from.bottom()) / 20);
        if (from.kind() == SceneNode.Kind.SLOT && below > 0) {
            double ex = end.getX();
            double ey = end.getY();
            return straight.towards(new Curve(sx, sy, sx, sy + (ey - sy) * 0.55,
                    ex - (ex - sx) * 0.25, ey - (ey - sy) * 0.25, ex, ey), below);
        }
        double sameRow = clamp(1 - (Math.abs(to.centerY() - from.centerY()) - 10) / 20);
        if (kind == SceneEdge.Kind.PREV_ARROW) {
            // A prev arrow normally points left, to the node before: straight. One pointing right
            // wraps round (a circular list's head.prev, to the tail), so it goes over the row,
            // while the tail's next comes back under it.
            double wraps = sameRow * clamp((to.centerX() - from.centerX() + 10) / 20);
            if (wraps <= 0) {
                return straight;
            }
            double high = Math.min(from.y(), to.y()) - 30;
            Curve over = new Curve(sx, sy, sx, high, to.centerX(), high, to.centerX(), to.y());
            return straight.towards(over, wraps);
        }
        double backwards = clamp((from.centerX() - to.centerX() + 10) / 20);
        double bend = sameRow * backwards;
        if (bend <= 0) {
            return straight;
        }
        double low = Math.max(from.bottom(), to.bottom()) + 30;
        Curve under = new Curve(sx, sy, sx, low, to.centerX(), low, to.centerX(), to.bottom());
        return straight.towards(under, bend);
    }

    // Where an arrow from above lands on a whole array: its first cell, not somewhere along its
    // length. (An array's outline is the kind of outline that has its type as a caption.)
    private static Point2D arrayEntry(SceneNode array, double fromY) {
        double x = array.x() + Math.min(array.w() / 2, Style.CELL_W / 2.0);
        return new Point2D.Double(x, fromY < array.centerY() ? array.y() : array.bottom());
    }

    private void arrowHead(Graphics2D g, Curve curve, double reach) {
        double size = 8 * Math.min(1, reach / 0.2);
        if (size < 1) {
            return;
        }
        double angle = curve.endAngle();
        Path2D head = new Path2D.Double();
        head.moveTo(curve.x3(), curve.y3());
        head.lineTo(curve.x3() - size * Math.cos(angle - 0.45), curve.y3() - size * Math.sin(angle - 0.45));
        head.lineTo(curve.x3() - size * Math.cos(angle + 0.45), curve.y3() - size * Math.sin(angle + 0.45));
        head.closePath();
        g.fill(head);
    }

    // UML's inheritance head: a hollow triangle on the parent's edge.
    private void hollowHead(Graphics2D g, Curve curve) {
        double size = 11;
        double angle = curve.endAngle();
        Path2D head = new Path2D.Double();
        head.moveTo(curve.x3(), curve.y3());
        head.lineTo(curve.x3() - size * Math.cos(angle - 0.5), curve.y3() - size * Math.sin(angle - 0.5));
        head.lineTo(curve.x3() - size * Math.cos(angle + 0.5), curve.y3() - size * Math.sin(angle + 0.5));
        head.closePath();
        Color line = g.getColor();
        g.setColor(Style.BACKGROUND);
        g.fill(head);
        g.setColor(line);
        g.setStroke(LINE);
        g.draw(head);
    }

    // Where a line from (px, py) towards the centre of a shape crosses the shape's edge.
    private static Point2D border(SceneNode node, double px, double py) {
        double cx = node.centerX();
        double cy = node.centerY();
        double dx = px - cx;
        double dy = py - cy;
        if (dx == 0 && dy == 0) {
            return new Point2D.Double(cx, cy);
        }
        if (node.kind() == SceneNode.Kind.CIRCLE || node.kind() == SceneNode.Kind.RED_NODE || node.kind() == SceneNode.Kind.BLACK_NODE
                || node.kind() == SceneNode.Kind.VISITED_NODE) {
            double length = Math.hypot(dx, dy);
            return new Point2D.Double(cx + dx / length * node.w() / 2, cy + dy / length * node.h() / 2);
        }
        double scaleX = dx == 0 ? Double.MAX_VALUE : (node.w() / 2) / Math.abs(dx);
        double scaleY = dy == 0 ? Double.MAX_VALUE : (node.h() / 2) / Math.abs(dy);
        double scale = Math.min(Math.min(scaleX, scaleY), 1);
        return new Point2D.Double(cx + dx * scale, cy + dy * scale);
    }

    // ---- shapes ----

    private void paintNode(Graphics2D g, SceneNode node) {
        SceneNode.Look look = node.look();
        if (look.alpha() <= 0.003) {
            return;
        }
        AffineTransform saved = g.getTransform();
        if (Math.abs(look.scale() - 1) > 0.001) { // growing in or shrinking out, around its centre
            g.translate(node.centerX(), node.centerY());
            g.scale(look.scale(), look.scale());
            g.translate(-node.centerX(), -node.centerY());
        }
        double alpha = look.alpha();
        Rectangle2D box = new Rectangle2D.Double(node.x(), node.y(), node.w(), node.h());
        switch (node.kind()) {
            case CELL -> {
                panel(g, box, Style.FILL, look.flash(), alpha);
                value(g, node, box, alpha);
                caption(g, node, alpha);
            }
            case SLOT -> {
                panel(g, box, Style.FILL_ALT, look.flash(), alpha);
                slotNumber(g, node, alpha);
            }
            case NULL_SLOT -> {
                panel(g, box, Style.FILL_ALT, look.flash(), alpha);
                setAlpha(g, alpha);
                g.setColor(Style.INK_SOFT);
                g.setStroke(HAIR);
                g.draw(new Line2D.Double(node.x() + 4, node.bottom() - 4, node.x() + node.w() - 4, node.y() + 4));
                caption(g, node, alpha);
            }
            case BOX -> {
                panel(g, box, Style.FILL, look.flash(), alpha);
                value(g, node, box, alpha);
            }
            case CIRCLE -> {
                Ellipse2D circle = new Ellipse2D.Double(node.x() + 0.5, node.y() + 0.5, node.w(), node.h());
                setAlpha(g, alpha);
                g.setColor(blend(Style.FILL, Style.CHANGE, look.flash()));
                g.fill(circle);
                g.setColor(Style.OUTLINE);
                g.setStroke(HAIR);
                g.draw(circle);
                value(g, node, circle, alpha);
            }
            case VISITED_NODE -> {
                Ellipse2D circle = new Ellipse2D.Double(node.x() + 0.5, node.y() + 0.5, node.w(), node.h());
                setAlpha(g, alpha);
                g.setColor(blend(Style.PRESSED, Style.CHANGE, look.flash()));
                g.fill(circle);
                g.setColor(Style.OUTLINE);
                g.setStroke(HAIR);
                g.draw(circle);
                value(g, node, circle, alpha);
            }
            case RED_NODE, BLACK_NODE -> {
                boolean red = node.kind() == SceneNode.Kind.RED_NODE;
                Ellipse2D circle = new Ellipse2D.Double(node.x() + 0.5, node.y() + 0.5, node.w(), node.h());
                setAlpha(g, alpha);
                g.setColor(blend(red ? Style.NODE_RED : Style.NODE_BLACK, Style.CHANGE, look.flash()));
                g.fill(circle);
                g.setColor(red ? Style.NODE_RED_EDGE : Style.OUTLINE);
                g.setStroke(HAIR);
                g.draw(circle);
                value(g, node, circle, alpha, red ? Style.NODE_RED_INK : Style.NODE_BLACK_INK);
            }
            case OBJECT -> object(g, node, box, alpha);
            case FIELD -> {
                if (look.flash() > 0.003) {
                    setAlpha(g, alpha);
                    g.setColor(blend(Style.FILL, Style.CHANGE, look.flash()));
                    g.fill(box);
                }
                textLeft(g, node.text(), Style.FIELD_FONT, Style.INK, node.x() + Style.BOX_PAD, node.centerY(), alpha);
            }
            case TEXT -> text(g, node.text(), Style.CAPTION_FONT, Style.MUTED, node.centerX(), node.centerY(), alpha);
            case LABEL -> text(g, node.text(), Style.LABEL_FONT, Style.ACCENT, node.centerX(), node.centerY(), alpha);
            case MARKER -> marker(g, node, alpha);
            case SIDE_MARKER -> sideMarker(g, node, alpha);
            case FOCUS_READ -> {
                tint(g, box, Style.READ_TINT * alpha);
                outline(g, box, DASHED, Style.ACCENT, alpha);
            }
            case FOCUS_WRITE -> {
                tint(g, box, Style.WRITE_TINT * alpha);
                outline(g, box, FOCUS, Style.ACCENT, alpha);
            }
            case FOCUS_OUT -> {
                outline(g, box, DASHED, Style.ERROR, alpha);
                text(g, "?", Style.VALUE_FONT, Style.ERROR, node.centerX(), node.centerY(), alpha);
                text(g, node.caption(), Style.CAPTION_FONT, Style.ERROR, node.centerX(),
                        node.bottom() + Style.CAPTION_H / 2.0 + 1, alpha);
            }
            case FLYER -> flyer(g, node, alpha);
            case FRAME -> {
                // invisible: only here for labels and arrows to point at
            }
        }
        g.setTransform(saved);
    }

    // A box with a fill (tinted amber while its value is flashing) and a thin grey outline.
    // The outline sits half a pixel in, so it's one sharp pixel wide when the box is still.
    private void panel(Graphics2D g, Rectangle2D box, Color fill, double flash, double alpha) {
        setAlpha(g, alpha);
        g.setColor(blend(fill, Style.CHANGE, flash));
        g.fill(box);
        g.setColor(Style.OUTLINE);
        g.setStroke(HAIR);
        g.draw(new Rectangle2D.Double(box.getX() + 0.5, box.getY() + 0.5, box.getWidth(), box.getHeight()));
    }

    // A see-through wash of blue. The value still shows through it, and so does a changed cell's amber.
    private void tint(Graphics2D g, Rectangle2D box, double amount) {
        setAlpha(g, amount);
        g.setColor(Style.ACCENT);
        g.fill(box);
    }

    // Drawn just inside the cell, so the outlines of neighbouring cells don't run into each other.
    private void outline(Graphics2D g, Rectangle2D box, BasicStroke stroke, Color color, double alpha) {
        setAlpha(g, alpha);
        g.setColor(color);
        g.setStroke(stroke);
        g.draw(new Rectangle2D.Double(box.getX() + 2, box.getY() + 2, box.getWidth() - 3, box.getHeight() - 3));
    }

    // The value inside a cell or node. While it changes, the old value rolls up and out and the
    // new one rolls in from below (clipped to the shape, like a counter).
    private void value(Graphics2D g, SceneNode node, Shape clip, double alpha) {
        value(g, node, clip, alpha, Style.INK);
    }

    private void value(Graphics2D g, SceneNode node, Shape clip, double alpha, Color ink) {
        SceneNode.Look look = node.look();
        double cx = node.centerX();
        double cy = node.centerY();
        if (look.oldText() == null || look.roll() >= 0.999) {
            text(g, node.text(), Style.VALUE_FONT, ink, cx, cy, alpha);
            return;
        }
        Shape savedClip = g.getClip();
        g.clip(clip);
        double roll = Math.max(0, Math.min(1, look.roll()));
        double shift = node.h() * 0.6;
        text(g, look.oldText(), Style.VALUE_FONT, ink, cx, cy - shift * roll, alpha * (1 - roll));
        text(g, node.text(), Style.VALUE_FONT, ink, cx, cy + shift * (1 - roll), alpha * roll);
        g.setClip(savedClip);
    }

    private void caption(Graphics2D g, SceneNode node, double alpha) {
        if (node.caption() != null) {
            text(g, node.caption(), Style.CAPTION_FONT, Style.MUTED, node.centerX(),
                    node.bottom() + Style.CAPTION_H / 2.0 + 1, alpha);
        }
    }

    // The index under a pointer slot. An arrow leaves from the middle of the slot, so the number
    // sits to one side, on a white patch: an arrow going that way passes behind it.
    private void slotNumber(Graphics2D g, SceneNode node, double alpha) {
        if (node.caption() == null) {
            return;
        }
        double cx = node.centerX() + 12;
        double cy = node.bottom() + Style.CAPTION_H / 2.0 + 1;
        double w = Style.textWidth(node.caption(), Style.CAPTION_FONT) + 4;
        setAlpha(g, alpha);
        g.setColor(Style.BACKGROUND);
        g.fill(new Rectangle2D.Double(cx - w / 2, cy - 6, w, 12));
        text(g, node.caption(), Style.CAPTION_FONT, Style.MUTED, cx, cy, alpha);
    }

    // A plain object: its class name in a grey title bar. Its fields are shapes of their own.
    private void object(Graphics2D g, SceneNode node, Rectangle2D box, double alpha) {
        setAlpha(g, alpha);
        g.setColor(blend(Style.FILL, Style.CHANGE, node.look().flash()));
        g.fill(box);
        g.setColor(Style.FILL_ALT);
        g.fill(new Rectangle2D.Double(node.x(), node.y(), node.w(), Style.TITLE_H));
        g.setColor(Style.HAIRLINE);
        g.setStroke(HAIR);
        g.draw(new Line2D.Double(node.x(), node.y() + Style.TITLE_H + 0.5, node.x() + node.w(), node.y() + Style.TITLE_H + 0.5));
        g.setColor(Style.OUTLINE);
        g.draw(new Rectangle2D.Double(node.x() + 0.5, node.y() + 0.5, node.w(), node.h()));
        text(g, node.caption(), Style.TITLE_FONT, Style.INK, node.centerX(), node.y() + Style.TITLE_H / 2.0, alpha);
    }

    // An index marker under a cell: a small caret pointing up at it, and the variable's name.
    private void marker(Graphics2D g, SceneNode node, double alpha) {
        double cx = node.centerX();
        Path2D caret = new Path2D.Double();
        caret.moveTo(cx, node.y() + 1);
        caret.lineTo(cx - 4, node.y() + 6);
        caret.lineTo(cx + 4, node.y() + 6);
        caret.closePath();
        setAlpha(g, alpha);
        g.setColor(Style.ACCENT);
        g.fill(caret);
        text(g, node.text(), Style.MARKER_FONT, Style.ACCENT, cx, node.y() + 6 + (node.h() - 6) / 2, alpha);
    }

    // A row marker beside a grid: the variable's name, then a caret pointing right at the row.
    private void sideMarker(Graphics2D g, SceneNode node, double alpha) {
        double tipX = node.x() + node.w() - 1;
        double cy = node.centerY();
        Path2D caret = new Path2D.Double();
        caret.moveTo(tipX, cy);
        caret.lineTo(tipX - 5, cy - 4);
        caret.lineTo(tipX - 5, cy + 4);
        caret.closePath();
        setAlpha(g, alpha);
        g.setColor(Style.ACCENT);
        g.fill(caret);
        text(g, node.text(), Style.MARKER_FONT, Style.ACCENT, node.x() + (node.w() - 8) / 2, cy, alpha);
    }

    // A copied value in flight: a small white card with a blue edge, lifted above the cells.
    private void flyer(Graphics2D g, SceneNode node, double alpha) {
        RoundRectangle2D card = new RoundRectangle2D.Double(node.x() + 3, node.y() + 4, node.w() - 6, node.h() - 8, 6, 6);
        setAlpha(g, alpha);
        g.setColor(Style.FILL);
        g.fill(card);
        g.setColor(Style.ACCENT);
        g.setStroke(HAIR);
        g.draw(card);
        text(g, node.text(), Style.VALUE_FONT, Style.INK, node.centerX(), node.centerY(), alpha);
    }

    // ---- text, drawn as glyph outlines so it can sit at fractional positions ----

    private void text(Graphics2D g, String text, Font font, Color color, double cx, double cy, double alpha) {
        if (text == null || text.isEmpty()) {
            return;
        }
        GlyphVector glyph = glyph(text, font);
        textAt(g, glyph, font, color, cx - glyph.getLogicalBounds().getWidth() / 2, cy, alpha);
    }

    private void textLeft(Graphics2D g, String text, Font font, Color color, double x, double cy, double alpha) {
        if (!text.isEmpty()) {
            textAt(g, glyph(text, font), font, color, x, cy, alpha);
        }
    }

    private void textAt(Graphics2D g, GlyphVector glyph, Font font, Color color, double x, double cy, double alpha) {
        if (alpha <= 0.003) {
            return;
        }
        LineMetrics metrics = font.getLineMetrics("0", Style.TEXT_CONTEXT);
        double baseline = cy + (metrics.getAscent() - metrics.getDescent()) / 2;
        setAlpha(g, alpha);
        g.setColor(color);
        g.fill(glyph.getOutline((float) x, (float) baseline));
    }

    private GlyphVector glyph(String text, Font font) {
        return glyphs.computeIfAbsent(font.getName() + font.getStyle() + font.getSize() + ":" + text,
                k -> font.createGlyphVector(Style.TEXT_CONTEXT, text));
    }

    private static void setAlpha(Graphics2D g, double alpha) {
        g.setComposite(AlphaComposite.SrcOver.derive((float) clamp(alpha)));
    }

    private static double clamp(double value) {
        return Math.max(0, Math.min(1, value));
    }

    // Mixes two colours: amount 0 gives a, amount 1 gives b.
    private static Color blend(Color a, Color b, double amount) {
        double t = clamp(amount);
        return new Color(
                (int) Math.round(a.getRed() + (b.getRed() - a.getRed()) * t),
                (int) Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                (int) Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * t));
    }

    // A cubic Bézier curve: a start, two control points, and an end. A straight line is just a
    // curve whose control points sit on the line, so straight and curved arrows can blend.
    private record Curve(double x0, double y0, double x1, double y1, double x2, double y2, double x3, double y3) {

        static Curve line(double ax, double ay, double bx, double by) {
            return new Curve(ax, ay, ax + (bx - ax) / 3, ay + (by - ay) / 3,
                    ax + 2 * (bx - ax) / 3, ay + 2 * (by - ay) / 3, bx, by);
        }

        // Part of the way from this curve to another one: t = 0 is this curve, t = 1 the other.
        Curve towards(Curve other, double t) {
            return new Curve(mix(x0, other.x0, t), mix(y0, other.y0, t), mix(x1, other.x1, t), mix(y1, other.y1, t),
                    mix(x2, other.x2, t), mix(y2, other.y2, t), mix(x3, other.x3, t), mix(y3, other.y3, t));
        }

        // Part of the way from this curve to another, turning around the start like a clock hand.
        // (Sliding the end straight across would pass through the start, and the arrow would vanish.)
        Curve swing(Curve other, double t) {
            double sx = mix(x0, other.x0, t);
            double sy = mix(y0, other.y0, t);
            double[] c1 = turn(x1 - x0, y1 - y0, other.x1 - other.x0, other.y1 - other.y0, t);
            double[] c2 = turn(x2 - x0, y2 - y0, other.x2 - other.x0, other.y2 - other.y0, t);
            double[] end = turn(x3 - x0, y3 - y0, other.x3 - other.x0, other.y3 - other.y0, t);
            return new Curve(sx, sy, sx + c1[0], sy + c1[1], sx + c2[0], sy + c2[1], sx + end[0], sy + end[1]);
        }

        // A point (given relative to the start) turned and stretched part of the way to another.
        // It takes the shorter turn; when the two point almost opposite ways, it turns the way
        // that passes underneath, so an arrow flipping from right to left swings below the nodes.
        private static double[] turn(double ax, double ay, double bx, double by, double t) {
            double fromAngle = Math.atan2(ay, ax);
            double delta = Math.IEEEremainder(Math.atan2(by, bx) - fromAngle, 2 * Math.PI);
            if (Math.abs(delta) > Math.toRadians(150)) {
                double otherWay = delta > 0 ? delta - 2 * Math.PI : delta + 2 * Math.PI;
                double down = Math.PI / 2; // y grows downwards, so +90° points down
                if (Math.abs(Math.IEEEremainder(fromAngle + otherWay / 2 - down, 2 * Math.PI))
                        < Math.abs(Math.IEEEremainder(fromAngle + delta / 2 - down, 2 * Math.PI))) {
                    delta = otherWay;
                }
            }
            double angle = fromAngle + delta * t;
            double radius = mix(Math.hypot(ax, ay), Math.hypot(bx, by), t);
            if (Math.abs(delta) > Math.PI / 2) {
                radius *= 1 - 0.45 * Math.sin(Math.PI * t); // tuck in while turning around
            }
            return new double[] {radius * Math.cos(angle), radius * Math.sin(angle)};
        }

        // The first part of the curve, from its start up to t (0 to 1), by de Casteljau's construction.
        Curve upTo(double t) {
            double ax = mix(x0, x1, t);
            double ay = mix(y0, y1, t);
            double bx = mix(x1, x2, t);
            double by = mix(y1, y2, t);
            double cx = mix(x2, x3, t);
            double cy = mix(y2, y3, t);
            double dx = mix(ax, bx, t);
            double dy = mix(ay, by, t);
            double ex = mix(bx, cx, t);
            double ey = mix(by, cy, t);
            return new Curve(x0, y0, ax, ay, dx, dy, mix(dx, ex, t), mix(dy, ey, t));
        }

        Path2D path() {
            Path2D path = new Path2D.Double();
            path.moveTo(x0, y0);
            path.curveTo(x1, y1, x2, y2, x3, y3);
            return path;
        }

        // The direction the curve is heading at its end, for the arrowhead.
        double endAngle() {
            double dx = x3 - x2;
            double dy = y3 - y2;
            if (Math.hypot(dx, dy) < 0.5) {
                dx = x3 - x0;
                dy = y3 - y0;
            }
            return Math.atan2(dy, dx);
        }

        private static double mix(double a, double b, double t) {
            return a + (b - a) * t;
        }
    }
}
