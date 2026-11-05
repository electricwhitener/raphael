package raphael.render;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.GraphicsEnvironment;
import java.awt.font.FontRenderContext;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

// Every colour, size, font and timing used for drawing, kept in one place.
// The look: plain like a terminal (white or dark), one monospace font, thin lines, a blue accent for
// "what's about to happen" (variables, the next line, the cells it touches) and amber for
// "what just changed".
// OOP: a class of shared settings. Static members, and a private constructor so no objects are made.
public final class Style {

    // Sizes, in pixels
    public static final int BOTTOM_ROW_H = 210;   // call stack, variables and console
    public static final int MARGIN = 24;
    public static final int SIDE_MARGIN = 68;     // left and right: room for a ghost cell just past either end
    public static final int STRUCTURE_GAP = 30;   // between two rows of structures
    public static final int ROW_GAP = 56;         // between two structures in one row, with room for a ghost cell
    public static final int LABEL_HEIGHT = 18;    // one variable label, like "curr"
    public static final int LABEL_SPACE = 2 * LABEL_HEIGHT + 6; // room above a structure for labels
    public static final int CELL_W = 44;
    public static final int CELL_H = 34;
    public static final int CAPTION_H = 16;       // index numbers next to cells
    public static final int MARKER_H = 20;        // one row of index markers (i, j) under an array
    public static final int ROW_MARKER_W = 46;    // room left of a grid for row markers
    public static final int NODE_W = 44;          // value part of a list node
    public static final int NODE_H = 34;
    public static final int SLOT_W = 18;          // pointer part of a list node or object field
    public static final int LIST_GAP = 36;        // space between list nodes, for the arrow
    public static final int CURVE_SPACE = 34;     // room under a list for arrows that point back
    public static final int CIRCLE = 40;          // tree node size
    public static final int TREE_X_GAP = 14;
    public static final int TREE_LEVEL_H = 66;
    public static final int CHILD_SLOT_H = 12;    // the row of child pointers under a B-tree node
    public static final int SUBTREE_GAP = 18;     // between neighbouring B-tree nodes
    public static final int LEVEL_GAP = 66;       // between a B-tree node and its children, with room for markers
    public static final int TITLE_H = 22;         // class name at the top of an object box
    public static final int LINE_H = 20;          // one field inside an object box
    public static final int BOX_PAD = 8;
    public static final int MAX_ROW_W = 640;      // a row of arrays or trees wraps onto a new row after this
    public static final int MAX_BOX_ROW_W = 900;  // objects stay together longer, so arrows don't run behind them
    public static final double ARROW_OVER_BOX = 0.35; // how strongly an array's arrow shows across a box

    // The window around the picture: gaps on a 4 px grid, and how round controls are. (The
    // picture's cells and nodes stay square.)
    public static final int SPACE_1 = 4;
    public static final int SPACE_2 = 8;
    public static final int SPACE_3 = 12;
    public static final int STATUS_H = 28;   // the status bar
    public static final int ARC = 8;         // corners of buttons and fields
    public static final int FOCUS_WIDTH = 2; // the ring around the focused control

    // Colours. These aren't final, because dark mode swaps them all at once in useTheme().
    // Everything reads them when it paints, so the next repaint uses the new ones.
    public static Color ON_ACCENT;     // text on a filled blue button (dark text in dark mode: white on that blue is too faint)
    public static final double WRITE_TINT = 0.16;       // how much blue fills a cell being written
    public static final double READ_TINT = 0.07;        // how much blue fills a cell being read
    public static Color BACKGROUND;
    public static Color INK;           // text
    public static Color INK_SOFT;      // arrows
    public static Color OUTLINE;       // the edges of boxes and circles
    public static Color MUTED;         // index numbers, hints
    public static Color HAIRLINE;      // thin separators
    public static Color BAR;           // the toolbar and status bar
    public static Color HOVER;         // a button under the mouse
    public static Color PRESSED;       // a button being pressed
    public static Color CONTROL_BORDER; // edges of fields and buttons (3:1 against the background, as WCAG asks)
    public static Color FILL;          // inside a cell
    public static Color FILL_ALT;      // pointer slots, title bars
    public static Color ACCENT;        // variables, the next line
    public static Color ACCENT_PRESSED; // the run button under the mouse
    public static Color ACCENT_TINT;
    public static Color CHANGE;        // a value that just changed
    public static Color CHANGE_TINT;
    public static Color ERROR;
    public static Color ERROR_TINT;
    public static Color CURRENT_LINE;  // the line about to run
    public static Color PREVIOUS_LINE; // the line that just ran
    public static Color ERROR_LINE;
    // Code colours, as in an IDE. None of them is the blue, amber or red that have a meaning above.
    public static Color CODE_KEYWORD;
    public static Color CODE_TYPE;
    public static Color CODE_METHOD;
    public static Color CODE_STRING;
    public static Color CODE_NUMBER;
    public static Color CODE_COMMENT;
    private static boolean dark;

    static {
        useTheme(false);
    }

    // Fonts: one monospace family everywhere (JetBrains Mono, which ships inside the app)
    public static final String MONO = pickMono();
    public static final Font UI_FONT = new Font(MONO, Font.PLAIN, 13);
    public static final int DEFAULT_CODE_SIZE = 13;
    public static final int MIN_CODE_SIZE = 10;
    public static final int MAX_CODE_SIZE = 28;
    public static Font CODE_FONT = new Font(MONO, Font.PLAIN, DEFAULT_CODE_SIZE); // Ctrl +/- changes its size
    public static final Font VALUE_FONT = new Font(MONO, Font.BOLD, 14);
    public static final Font FIELD_FONT = new Font(MONO, Font.PLAIN, 13);
    public static final Font LABEL_FONT = new Font(MONO, Font.BOLD, 13);
    public static final Font MARKER_FONT = new Font(MONO, Font.BOLD, 12);
    public static final Font CAPTION_FONT = new Font(MONO, Font.PLAIN, 11);
    public static final Font TITLE_FONT = new Font(MONO, Font.BOLD, 12);
    public static final Font SECTION_FONT = new Font(MONO, Font.BOLD, 11);

    // Motion. Shapes move on springs: "response" is roughly how long a move takes, in seconds.
    public static final double SPRING_RESPONSE = 0.35;
    public static final double FADE_IN = 0.07;     // seconds; appearing shapes
    public static final double FADE_OUT = 0.045;   // leaving is faster than arriving
    public static final double ROLL = 0.06;        // a changed value rolling in
    public static final double FLASH_DECAY = 0.3;
    public static final double CHANGED_GLOW = 0.35; // how much a changed value stays highlighted
    public static final double ENTER_SCALE = 0.92;  // new shapes grow from 92% of their size
    public static final double EXIT_SCALE = 0.96;
    public static final int FRAME_MS = 10;          // the animation clock ticks this often
    public static final int PLAY_STEP_MS = 700;     // one step while playing at 1× speed

    // Text is measured and drawn with the same settings: smooth edges and fractional widths.
    public static final FontRenderContext TEXT_CONTEXT = new FontRenderContext(null, true, true);

    private Style() {
    }

    // Switches every colour to the light or the dark set: pick(light, dark).
    public static void useTheme(boolean darkTheme) {
        dark = darkTheme;
        BACKGROUND = pick(0xFFFFFF, 0x0D1117);
        INK = pick(0x1F2328, 0xE6EDF3);
        INK_SOFT = pick(0x57606A, 0xA0A9B4);
        OUTLINE = pick(0x6E7781, 0x768390);
        MUTED = pick(0x656D76, 0x9198A1); // 5.3:1 and 6.5:1, readable for hints and small numbers
        HAIRLINE = pick(0xD0D7DE, 0x30363D);
        BAR = pick(0xF6F8FA, 0x161B22);
        HOVER = pick(0xEAEEF2, 0x262C36);
        PRESSED = pick(0xD8DEE4, 0x30363D);
        CONTROL_BORDER = pick(0x87909A, 0x656D76);
        ON_ACCENT = pick(0xFFFFFF, 0x0D1117);
        FILL = pick(0xFFFFFF, 0x161B22);
        FILL_ALT = pick(0xF6F8FA, 0x21262D);
        ACCENT = pick(0x0969DA, 0x4493F8);
        ACCENT_PRESSED = pick(0x0757B3, 0x1F6FEB);
        ACCENT_TINT = pick(0xDDF4FF, 0x12304F);
        CHANGE = pick(0xF2CC60, 0x9E6A03);
        CHANGE_TINT = pick(0xFFF8C5, 0x4D3800);
        ERROR = pick(0xCF222E, 0xF85149);
        ERROR_TINT = pick(0xFFEBE9, 0x4B1113);
        CURRENT_LINE = ACCENT_TINT;
        PREVIOUS_LINE = pick(0xEFF1F3, 0x21262D);
        ERROR_LINE = ERROR_TINT;
        CODE_KEYWORD = pick(0x8250DF, 0xD2A8FF);
        CODE_TYPE = pick(0x1B7C83, 0x39C5CF);
        CODE_METHOD = pick(0x0A3069, 0xB6D6FF);
        CODE_STRING = pick(0x116329, 0x7EE787);
        CODE_NUMBER = pick(0x9A4A00, 0xFFA657);
        CODE_COMMENT = pick(0x6E7781, 0x8B949E);
    }

    public static boolean isDark() {
        return dark;
    }

    private static Color pick(int light, int darkColor) {
        return new Color(dark ? darkColor : light);
    }

    // Changes the size of the code and panel text, within sensible limits.
    public static void setCodeSize(int size) {
        CODE_FONT = CODE_FONT.deriveFont((float) Math.max(MIN_CODE_SIZE, Math.min(MAX_CODE_SIZE, size)));
    }

    public static double textWidth(String text, Font font) {
        return font.getStringBounds(text, TEXT_CONTEXT).getWidth();
    }

    // The font that ships inside the app, so it looks the same on every computer. If it can't be
    // loaded: the first good monospace font that's installed. On Linux, Java's "Monospaced" is
    // DejaVu Sans Mono, with fallbacks for symbols that font doesn't have.
    private static String pickMono() {
        String bundled = loadBundledMono();
        if (bundled != null) {
            return bundled;
        }
        List<String> installed = Arrays.asList(
                GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames());
        for (String family : new String[] {"Cascadia Mono", "Consolas", "SF Mono", "Menlo"}) {
            if (installed.contains(family)) {
                return family;
            }
        }
        return Font.MONOSPACED;
    }

    // Registers the regular and bold font files with Java, and returns their family name.
    private static String loadBundledMono() {
        try (InputStream regular = Style.class.getResourceAsStream("/fonts/JetBrainsMonoNL-Regular.ttf");
             InputStream bold = Style.class.getResourceAsStream("/fonts/JetBrainsMonoNL-Bold.ttf")) {
            GraphicsEnvironment fonts = GraphicsEnvironment.getLocalGraphicsEnvironment();
            Font font = Font.createFont(Font.TRUETYPE_FONT, regular);
            fonts.registerFont(font);
            fonts.registerFont(Font.createFont(Font.TRUETYPE_FONT, bold));
            return font.getFamily();
        } catch (IOException | FontFormatException | NullPointerException e) {
            Logger.getLogger(Style.class.getName()).log(Level.WARNING, "The bundled font didn't load; using another monospace font", e);
            return null; // the files are missing or damaged
        }
    }
}
