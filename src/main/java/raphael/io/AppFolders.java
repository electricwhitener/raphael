package raphael.io;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;

// Where Raphael keeps its own files (the autosaved draft, the logs). Each system has its own
// usual place, so Raphael doesn't scatter files in the home folder:
//   Windows: %LOCALAPPDATA%\Raphael          logs in …\Raphael\logs
//   macOS:   ~/Library/Application Support/Raphael   logs in ~/Library/Logs/Raphael
//   Linux:   ~/.local/share/raphael ($XDG_DATA_HOME)  logs in ~/.local/state/raphael/logs ($XDG_STATE_HOME)
// OOP: a utility class (static methods, private constructor); overloading: data() reads this
// computer, data(os, env, home) takes them as parameters so a test can try every system.
public final class AppFolders {

    private AppFolders() {
    }

    public static Path data() throws IOException {
        return Files.createDirectories(data(System.getProperty("os.name", ""), System.getenv(), System.getProperty("user.home")));
    }

    public static Path logs() throws IOException {
        return Files.createDirectories(logs(System.getProperty("os.name", ""), System.getenv(), System.getProperty("user.home")));
    }

    static Path data(String os, Map<String, String> env, String home) {
        String name = os.toLowerCase(Locale.ROOT);
        if (name.startsWith("windows")) {
            String local = env.get("LOCALAPPDATA");
            return (local != null ? Path.of(local) : Path.of(home, "AppData", "Local")).resolve("Raphael");
        }
        if (name.startsWith("mac")) {
            return Path.of(home, "Library", "Application Support", "Raphael");
        }
        String xdg = env.get("XDG_DATA_HOME");
        return (xdg != null && !xdg.isBlank() ? Path.of(xdg) : Path.of(home, ".local", "share")).resolve("raphael");
    }

    static Path logs(String os, Map<String, String> env, String home) {
        String name = os.toLowerCase(Locale.ROOT);
        if (name.startsWith("windows")) {
            return data(os, env, home).resolve("logs");
        }
        if (name.startsWith("mac")) {
            return Path.of(home, "Library", "Logs", "Raphael");
        }
        String xdg = env.get("XDG_STATE_HOME");
        return (xdg != null && !xdg.isBlank() ? Path.of(xdg) : Path.of(home, ".local", "state")).resolve("raphael").resolve("logs");
    }
}
