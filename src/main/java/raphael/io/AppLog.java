package raphael.io;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.logging.FileHandler;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

// Raphael's log file, using the JDK's own logging (java.util.logging). Each class that has
// something to report gets a Logger named after itself, and everything under "raphael" ends up
// in raphael-0.log in AppFolders.logs(). The newest file is always raphael-0.log; when it
// reaches 1 MB it becomes raphael-1.log, and so on, keeping 3 files. Nothing is ever sent
// anywhere: the log only helps when someone reports a problem.
// OOP: inheritance and overriding (LineFormatter extends Java's Formatter and overrides format);
// a utility class with a private constructor; static members.
public final class AppLog {

    private static final int FILE_LIMIT = 1_000_000; // bytes per file
    private static final int FILE_COUNT = 3;
    private static Path folder; // null until setup() worked

    private AppLog() {
    }

    // Called once, when Raphael starts. If the log can't be opened, Raphael runs anyway, and
    // the messages go to the console only.
    public static void setup() {
        Logger raphael = Logger.getLogger("raphael");
        try {
            Path logs = AppFolders.logs();
            FileHandler file = new FileHandler(logs.resolve("raphael-%g.log").toString(), FILE_LIMIT, FILE_COUNT, true);
            file.setFormatter(new LineFormatter());
            file.setEncoding("UTF-8");
            raphael.addHandler(file);
            raphael.setUseParentHandlers(false); // the file only, not the console window as well
            folder = logs;
        } catch (IOException | SecurityException e) {
            raphael.log(Level.WARNING, "No log file; messages go to the console only", e);
        }
        raphael.info("Raphael started: Java " + System.getProperty("java.version") + " on "
                + System.getProperty("os.name") + " " + System.getProperty("os.version"));
    }

    // The folder the log files are in, or null if there is no log file.
    public static Path folder() {
        return folder;
    }

    // One line per message: "2026-11-05 10:22:01 WARNING JdiTracer: …", then the stack trace if any.
    static class LineFormatter extends Formatter {

        private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        @Override
        public String format(LogRecord record) {
            String source = record.getLoggerName() == null ? "" : record.getLoggerName();
            source = source.substring(source.lastIndexOf('.') + 1);
            StringBuilder line = new StringBuilder()
                    .append(LocalDateTime.ofInstant(record.getInstant(), ZoneId.systemDefault()).format(TIME)).append(' ')
                    .append(record.getLevel().getName()).append(' ')
                    .append(source).append(": ")
                    .append(formatMessage(record)).append(System.lineSeparator());
            if (record.getThrown() != null) {
                StringWriter trace = new StringWriter();
                record.getThrown().printStackTrace(new PrintWriter(trace));
                line.append(trace);
            }
            return line.toString();
        }
    }
}
