package raphael.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;

class DialogsTest {

    // Each system's own button order: Windows puts the main button first, macOS and Linux last.
    @Test
    void buttonsComeInEachSystemsOrder() {
        List<String> others = List.of("Don't save");
        assertEquals(List.of("Save", "Don't save", "Cancel"), Dialogs.order("Save", "Cancel", others, true, false));
        assertEquals(List.of("Don't save", "Cancel", "Save"), Dialogs.order("Save", "Cancel", others, false, true));
        assertEquals(List.of("Cancel", "Don't save", "Save"), Dialogs.order("Save", "Cancel", others, false, false));
        assertEquals(List.of("OK"), Dialogs.order("OK", null, List.of(), false, false));
    }

    @Test
    void theDraftsTimeReadsNaturally() {
        LocalDateTime now = LocalDateTime.of(2026, 11, 5, 10, 0);
        assertEquals("today at 09:15", MainWindow.whenText(now.withHour(9).withMinute(15).atZone(ZoneId.systemDefault()).toInstant(), now));
        assertEquals("yesterday at 22:40", MainWindow.whenText(now.minusDays(1).withHour(22).withMinute(40).atZone(ZoneId.systemDefault()).toInstant(), now));
        assertEquals("1 Nov at 08:05", MainWindow.whenText(LocalDateTime.of(2026, 11, 1, 8, 5).atZone(ZoneId.systemDefault()).toInstant(), now));
    }
}
