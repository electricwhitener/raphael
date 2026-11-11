package raphael.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class StyleTest {

    @Test
    void darkModeSwapsTheColoursAndBack() {
        java.awt.Color light = Style.BACKGROUND;
        Style.useTheme(true);
        assertEquals(true, Style.isDark());
        assertNotEquals(light, Style.BACKGROUND);
        assertTrue(contrast(Style.INK, Style.BACKGROUND) > 7, "text is easy to read on the background");
        assertTrue(contrast(Style.INK, Style.CHANGE) > 3, "a value stays readable while it flashes");
        assertTrue(contrast(Style.ACCENT, Style.BACKGROUND) > 4.5);
        Style.useTheme(false);
        assertEquals(light, Style.BACKGROUND);
        assertTrue(contrast(Style.INK, Style.CHANGE) > 3);
    }

    // WCAG AA in both themes: 4.5:1 for text, 3:1 for the edges of controls.
    @Test
    void hintsButtonsAndControlsAreReadableInBothThemes() {
        for (boolean dark : new boolean[] {false, true}) {
            Style.useTheme(dark);
            String theme = dark ? "dark" : "light";
            assertTrue(contrast(Style.MUTED, Style.BACKGROUND) >= 4.5, theme + ": hints");
            assertTrue(contrast(Style.MUTED, Style.BAR) >= 4.5, theme + ": hints on the bars");
            assertTrue(contrast(Style.ON_ACCENT, Style.ACCENT) >= 4.5, theme + ": the run button's text");
            assertTrue(contrast(Style.CONTROL_BORDER, Style.BACKGROUND) >= 3, theme + ": edges of fields");
            assertTrue(contrast(Style.INK, Style.BAR) >= 7, theme + ": text on the bars");
        }
        Style.useTheme(false);
    }

    @Test
    void textSizeStaysWithinLimits() {
        Style.setCodeSize(500);
        assertEquals(Style.MAX_CODE_SIZE, Style.CODE_FONT.getSize());
        Style.setCodeSize(1);
        assertEquals(Style.MIN_CODE_SIZE, Style.CODE_FONT.getSize());
        Style.setCodeSize(Style.DEFAULT_CODE_SIZE);
    }

    // The standard contrast ratio between two colours, from 1 (same) to 21 (black on white).
    private static double contrast(java.awt.Color a, java.awt.Color b) {
        double la = luminance(a);
        double lb = luminance(b);
        return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
    }

    private static double luminance(java.awt.Color c) {
        return 0.2126 * channel(c.getRed()) + 0.7152 * channel(c.getGreen()) + 0.0722 * channel(c.getBlue());
    }

    private static double channel(int v) {
        double s = v / 255.0;
        return s <= 0.03928 ? s / 12.92 : Math.pow((s + 0.055) / 1.055, 2.4);
    }

    @Test
    void theBundledFontLoadsAndHasTheSymbolsWeUse() {
        assertEquals("IBM Plex Mono", Style.MONO);
        assertEquals(-1, Style.UI_FONT.canDisplayUpTo("→…·—"));
    }
}
