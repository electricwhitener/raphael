package raphael.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import javax.swing.Icon;
import raphael.render.Style;

// The toolbar's icons, drawn with Java 2D on a 16 px grid: no image files, so they stay sharp at
// any screen scaling, and no icon library. The colour is read from Style each time an icon is
// painted, so icons follow dark mode, and a disabled button's icon is dimmed.
// OOP: an enum (each Glyph knows how to draw itself, by overriding draw()); a nested class that
// implements Swing's Icon interface; composition (an Icon holds a Glyph and a colour role).
public final class Icons {

    // Which colour an icon takes.
    public enum Role {
        TOOL,      // on the toolbar: soft ink, dimmed when the button is disabled
        ON_ACCENT, // on the filled Run button
        ON_INK     // on the filled Stop button
    }

    public enum Glyph {
        NEW {
            @Override
            void draw(Graphics2D g) {
                g.draw(new RoundRectangle2D.Float(3.5f, 2, 9, 12, 2, 2));
                g.draw(new Line2D.Float(8, 6, 8, 10));
                g.draw(new Line2D.Float(6, 8, 10, 8));
            }
        },
        OPEN {
            @Override
            void draw(Graphics2D g) {
                g.draw(path(false, 2, 12.5, 2, 4, 6, 4, 7.5, 5.5, 13, 5.5, 13, 7));
                g.draw(path(true, 2, 12.5, 4, 7.5, 14.5, 7.5, 12.5, 12.5));
            }
        },
        SAVE {
            @Override
            void draw(Graphics2D g) {
                g.draw(new RoundRectangle2D.Float(2.5f, 2.5f, 11, 11, 2, 2));
                g.draw(new Rectangle2D.Float(5, 2.5f, 5, 3.5f));
                g.draw(new Rectangle2D.Float(4.5f, 9, 7, 4.5f));
            }
        },
        PLAY {
            @Override
            void draw(Graphics2D g) {
                g.fill(path(true, 4, 2.5, 13, 8, 4, 13.5));
            }
        },
        PAUSE {
            @Override
            void draw(Graphics2D g) {
                g.fill(new RoundRectangle2D.Float(3.5f, 3, 3, 10, 1, 1));
                g.fill(new RoundRectangle2D.Float(9.5f, 3, 3, 10, 1, 1));
            }
        },
        STOP {
            @Override
            void draw(Graphics2D g) {
                g.fill(new RoundRectangle2D.Float(3.5f, 3.5f, 9, 9, 2, 2));
            }
        },
        FIRST {
            @Override
            void draw(Graphics2D g) {
                g.draw(path(false, 12, 3, 7, 8, 12, 13));
                g.draw(new Line2D.Float(4, 3, 4, 13));
            }
        },
        BACK {
            @Override
            void draw(Graphics2D g) {
                g.draw(path(false, 10, 3, 5, 8, 10, 13));
            }
        },
        FORWARD {
            @Override
            void draw(Graphics2D g) {
                g.draw(path(false, 6, 3, 11, 8, 6, 13));
            }
        },
        LAST {
            @Override
            void draw(Graphics2D g) {
                g.draw(path(false, 4, 3, 9, 8, 4, 13));
                g.draw(new Line2D.Float(12, 3, 12, 13));
            }
        },
        STEP_OVER { // an arc jumping over a dot: go past the call
            @Override
            void draw(Graphics2D g) {
                g.draw(new Arc2D.Float(2.5f, 3, 11, 10, 20, 140, Arc2D.OPEN));
                g.fill(path(true, 11, 3.2, 14.4, 6, 10.4, 7.6));
                g.fill(new Ellipse2D.Float(6.3f, 10.3f, 3.4f, 3.4f));
            }
        },
        STEP_OUT { // an arrow up and out of the method
            @Override
            void draw(Graphics2D g) {
                g.draw(new Line2D.Float(8, 12.5f, 8, 3.5f));
                g.draw(path(false, 4.5, 7, 8, 3.5, 11.5, 7));
                g.draw(new Line2D.Float(3.5f, 13.5f, 12.5f, 13.5f));
            }
        },
        THEME { // a half-filled circle: light and dark
            @Override
            void draw(Graphics2D g) {
                g.draw(new Ellipse2D.Float(2.5f, 2.5f, 11, 11));
                g.fill(new Arc2D.Float(2.5f, 2.5f, 11, 11, 90, 180, Arc2D.CHORD));
            }
        },
        SETTINGS { // three sliders
            @Override
            void draw(Graphics2D g) {
                Color ink = g.getColor();
                g.draw(new Line2D.Float(2, 4.5f, 14, 4.5f));
                g.draw(new Line2D.Float(2, 8, 14, 8));
                g.draw(new Line2D.Float(2, 11.5f, 14, 11.5f));
                float[][] knobs = {{9.5f, 2.5f}, {3, 6}, {8, 9.5f}};
                for (float[] knob : knobs) {
                    g.setColor(Style.BAR); // the knob hides the line under it
                    g.fill(new Ellipse2D.Float(knob[0], knob[1], 4, 4));
                    g.setColor(ink);
                    g.draw(new Ellipse2D.Float(knob[0] + 0.3f, knob[1] + 0.3f, 3.4f, 3.4f));
                }
            }
        },
        HELP {
            @Override
            void draw(Graphics2D g) {
                g.draw(new Ellipse2D.Float(2.5f, 2.5f, 11, 11));
                g.setFont(Style.sans(true, 9.5f));
                g.drawString("?", 5.6f, 11.4f);
            }
        },
        PASTE { // a clipboard
            @Override
            void draw(Graphics2D g) {
                g.draw(new RoundRectangle2D.Float(3, 3.5f, 10, 10.5f, 2, 2));
                g.draw(new RoundRectangle2D.Float(6, 2, 4, 3, 1, 1));
                g.draw(new Line2D.Float(5.5f, 8.5f, 10.5f, 8.5f));
                g.draw(new Line2D.Float(5.5f, 11, 9, 11));
            }
        },
        CHEVRON_DOWN {
            @Override
            void draw(Graphics2D g) {
                g.draw(path(false, 4, 6, 8, 10, 12, 6));
            }
        },
        CHEVRON_UP {
            @Override
            void draw(Graphics2D g) {
                g.draw(path(false, 4, 10, 8, 6, 12, 10));
            }
        },
        CLOSE { // a cross
            @Override
            void draw(Graphics2D g) {
                g.draw(new Line2D.Float(4.5f, 4.5f, 11.5f, 11.5f));
                g.draw(new Line2D.Float(11.5f, 4.5f, 4.5f, 11.5f));
            }
        };

        // Draws the glyph in a 16 × 16 box at (0, 0), in the graphics' current colour.
        abstract void draw(Graphics2D g);
    }

    public static final int SIZE = 16;

    private Icons() {
    }

    public static Icon of(Glyph glyph, Role role) {
        return new GlyphIcon(glyph, role);
    }

    private static Path2D path(boolean closed, double... points) {
        Path2D path = new Path2D.Double();
        path.moveTo(points[0], points[1]);
        for (int i = 2; i < points.length; i += 2) {
            path.lineTo(points[i], points[i + 1]);
        }
        if (closed) {
            path.closePath();
        }
        return path;
    }

    private static final class GlyphIcon implements Icon {

        private final Glyph glyph;
        private final Role role;

        GlyphIcon(Glyph glyph, Role role) {
            this.glyph = glyph;
            this.role = role;
        }

        @Override
        public void paintIcon(Component owner, Graphics graphics, int x, int y) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            g.translate(x, y);
            g.setColor(switch (role) {
                case ON_ACCENT -> Style.ON_ACCENT;
                case ON_INK -> Style.BACKGROUND;
                default -> owner == null || owner.isEnabled() ? Style.INK_SOFT : Style.ICON_DISABLED;
            });
            g.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            glyph.draw(g);
            g.dispose();
        }

        @Override
        public int getIconWidth() {
            return SIZE;
        }

        @Override
        public int getIconHeight() {
            return SIZE;
        }
    }
}
