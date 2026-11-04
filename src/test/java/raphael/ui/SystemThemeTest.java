package raphael.ui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SystemThemeTest {

    @Test
    void readsTheWindowsAnswer() {
        String key = "\r\nHKEY_CURRENT_USER\\Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize\r\n";
        assertTrue(SystemTheme.isDarkAnswer(key + "    AppsUseLightTheme    REG_DWORD    0x0\r\n"));
        assertFalse(SystemTheme.isDarkAnswer(key + "    AppsUseLightTheme    REG_DWORD    0x1\r\n"));
    }

    @Test
    void readsTheMacAndLinuxAnswers() {
        assertTrue(SystemTheme.isDarkAnswer("Dark\n"));
        assertTrue(SystemTheme.isDarkAnswer("'prefer-dark'\n"));
        assertFalse(SystemTheme.isDarkAnswer("'default'\n"));
    }

    @Test
    void noAnswerMeansLight() {
        assertFalse(SystemTheme.isDarkAnswer(null));
    }

    @Test
    void askingNeverFails() {
        SystemTheme.isDark(); // whatever this computer has, it returns instead of throwing
    }
}
