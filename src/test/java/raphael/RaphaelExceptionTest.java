package raphael;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.io.IOException;
import org.junit.jupiter.api.Test;
import raphael.compile.CompileSetupException;
import raphael.trace.TraceException;

class RaphaelExceptionTest {

    // The window catches RaphaelException alone, so every problem Raphael can explain must be one,
    // and must keep the low-level error that caused it.
    @Test
    void raphaelsProblemsAreOneFamilyAndKeepTheirCause() {
        IOException cause = new IOException("no space left");
        RaphaelException setup = new CompileSetupException("Couldn't write the code", cause);
        RaphaelException trace = new TraceException("Could not start the program", cause);
        assertSame(cause, setup.getCause());
        assertSame(cause, trace.getCause());
        assertInstanceOf(Exception.class, setup); // checked: callers must handle it
    }
}
