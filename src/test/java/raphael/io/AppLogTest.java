package raphael.io;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.logging.Level;
import java.util.logging.LogRecord;
import org.junit.jupiter.api.Test;

class AppLogTest {

    @Test
    void oneLineWithTheTimeLevelAndClassThenTheStackTrace() {
        LogRecord record = new LogRecord(Level.WARNING, "Autosave failed");
        record.setLoggerName("raphael.ui.MainWindow");
        record.setThrown(new IllegalStateException("disk full"));
        String text = new AppLog.LineFormatter().format(record);
        assertTrue(text.matches("(?s)\\d{4}-\\d\\d-\\d\\d \\d\\d:\\d\\d:\\d\\d WARNING MainWindow: Autosave failed\\R"
                + "java.lang.IllegalStateException: disk full.*"), text);
    }
}
