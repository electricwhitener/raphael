package raphael.ui;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

// Whether the computer is set to dark mode, so Raphael can match it the first time it starts.
// Java can't ask this directly, so it runs the small command each system has for it:
//   Windows (and WSL): reg query …\Personalize /v AppsUseLightTheme   → 0x0 means dark
//   macOS:             defaults read -g AppleInterfaceStyle            → "Dark"
//   Linux (GNOME):     gsettings get org.gnome.desktop.interface color-scheme → 'prefer-dark'
// Any problem (no such command, no answer within 2 seconds) just means "not dark".
// OOP: a utility class (static methods, private constructor); exception handling: a multi-catch
// of the checked exceptions a process can throw, and finally to make sure it never lingers.
public final class SystemTheme {

    private static final String PERSONALIZE = "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize";

    private SystemTheme() {
    }

    public static boolean isDark() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.startsWith("windows")) {
            return isDarkAnswer(run(List.of("reg", "query", PERSONALIZE, "/v", "AppsUseLightTheme")));
        }
        if (os.startsWith("mac")) {
            return isDarkAnswer(run(List.of("defaults", "read", "-g", "AppleInterfaceStyle")));
        }
        if (System.getenv("WSL_DISTRO_NAME") != null) { // Linux inside Windows: ask Windows
            return isDarkAnswer(run(List.of("reg.exe", "query", PERSONALIZE, "/v", "AppsUseLightTheme")));
        }
        return isDarkAnswer(run(List.of("gsettings", "get", "org.gnome.desktop.interface", "color-scheme")));
    }

    // Reads any of the answers above. Kept separate so it can be tested on any computer.
    static boolean isDarkAnswer(String answer) {
        if (answer == null) {
            return false;
        }
        String text = answer.toLowerCase(Locale.ROOT);
        if (text.contains("appsuselighttheme")) {
            return text.matches("(?s).*appsuselighttheme\\s+reg_dword\\s+0x0\\b.*");
        }
        return text.contains("dark");
    }

    // Runs a command and returns what it printed, or null if it failed or took too long.
    private static String run(List<String> command) {
        Process process = null;
        try {
            process = new ProcessBuilder(command).redirectErrorStream(true).start();
            if (!process.waitFor(2, TimeUnit.SECONDS) || process.exitValue() != 0) {
                return null;
            }
            try (InputStream out = process.getInputStream()) {
                return new String(out.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (IOException | InterruptedException e) {
            return null; // no such command on this computer, or we were interrupted
        } finally {
            if (process != null) {
                process.destroy();
            }
        }
    }
}
