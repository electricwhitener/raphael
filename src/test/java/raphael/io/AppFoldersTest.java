package raphael.io;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AppFoldersTest {

    @Test
    void windowsUsesLocalAppData() {
        Map<String, String> env = Map.of("LOCALAPPDATA", "C:/Users/ada/AppData/Local");
        assertEquals(Path.of("C:/Users/ada/AppData/Local", "Raphael"), AppFolders.data("Windows 11", env, "C:/Users/ada"));
        assertEquals(Path.of("C:/Users/ada/AppData/Local", "Raphael", "logs"), AppFolders.logs("Windows 11", env, "C:/Users/ada"));
        assertEquals(Path.of("C:/Users/ada", "AppData", "Local", "Raphael"), AppFolders.data("Windows 11", Map.of(), "C:/Users/ada"));
    }

    @Test
    void macUsesLibrary() {
        assertEquals(Path.of("/Users/ada", "Library", "Application Support", "Raphael"), AppFolders.data("Mac OS X", Map.of(), "/Users/ada"));
        assertEquals(Path.of("/Users/ada", "Library", "Logs", "Raphael"), AppFolders.logs("Mac OS X", Map.of(), "/Users/ada"));
    }

    @Test
    void linuxFollowsXdg() {
        assertEquals(Path.of("/home/ada", ".local", "share", "raphael"), AppFolders.data("Linux", Map.of(), "/home/ada"));
        assertEquals(Path.of("/home/ada", ".local", "state", "raphael", "logs"), AppFolders.logs("Linux", Map.of(), "/home/ada"));
        assertEquals(Path.of("/data", "raphael"), AppFolders.data("Linux", Map.of("XDG_DATA_HOME", "/data"), "/home/ada"));
        assertEquals(Path.of("/state", "raphael", "logs"), AppFolders.logs("Linux", Map.of("XDG_STATE_HOME", "/state"), "/home/ada"));
    }
}
