package raphael.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ErrorReporterTest {

    private final List<LogRecord> logged = new CopyOnWriteArrayList<>();
    private final Handler catcher = new Handler() {
        @Override
        public void publish(LogRecord record) {
            logged.add(record);
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }
    };
    private Thread.UncaughtExceptionHandler before;

    @BeforeEach
    void listen() {
        ErrorReporter.dialogs = false;
        before = Thread.getDefaultUncaughtExceptionHandler();
        Logger.getLogger(ErrorReporter.class.getName()).addHandler(catcher);
    }

    @AfterEach
    void stopListening() {
        Logger.getLogger(ErrorReporter.class.getName()).removeHandler(catcher);
        Thread.setDefaultUncaughtExceptionHandler(before);
        ErrorReporter.dialogs = true;
    }

    // An exception nothing catches, on any thread, ends up in the log instead of vanishing.
    @Test
    void anUncaughtExceptionIsLogged() throws InterruptedException {
        ErrorReporter.install();
        Thread broken = new Thread(() -> {
            throw new IllegalStateException("a bug in Raphael");
        });
        broken.start();
        broken.join();
        assertEquals(1, logged.size());
        assertEquals("a bug in Raphael", logged.get(0).getThrown().getMessage());
    }

    @Test
    void theDetailsHaveTheVersionsAndTheStackTrace() {
        String details = ErrorReporter.details(new IllegalStateException("oops"));
        assertTrue(details.startsWith("Raphael on Java " + System.getProperty("java.version")));
        assertTrue(details.contains("java.lang.IllegalStateException: oops"));
        assertTrue(details.contains("at raphael.ui.ErrorReporterTest"));
    }
}
