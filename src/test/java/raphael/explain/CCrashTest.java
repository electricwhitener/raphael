package raphael.explain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

// What an aborted C program printed, read the same on every system.
class CCrashTest {

    @Test
    void assertMessagesFromEachSystem() {
        assertEquals("assert(n >= 0) failed", CCrash.detail("SIGABRT", null, null,
                "program: main.c:5: main: Assertion `n >= 0' failed.\n")); // Linux (glibc)
        assertEquals("assert(n >= 0) failed", CCrash.detail("SIGABRT", null, null,
                "Assertion failed: (n >= 0), function main, file main.c, line 5.\n")); // macOS
        assertEquals("assert(n >= 0) failed", CCrash.detail("SIGABRT", null, null,
                "Assertion failed: n >= 0, file main.c, line 5\n")); // Windows (mingw)
        assertEquals("free() was called twice on the same block", CCrash.detail("SIGABRT", null, null,
                "free(): double free of a block\n"));
    }
}
