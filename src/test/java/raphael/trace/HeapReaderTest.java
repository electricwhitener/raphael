package raphael.trace;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class HeapReaderTest {

    // An empty char[] cell holds '\0', which has no glyph; it's written the way Java code writes it.
    @Test
    void charsWithoutAGlyphAreWrittenAsEscapes() {
        assertEquals("'a'", HeapReader.charLiteral('a'));
        assertEquals("'\\0'", HeapReader.charLiteral('\0'));
        assertEquals("'\\n'", HeapReader.charLiteral('\n'));
        assertEquals("'\\u0007'", HeapReader.charLiteral('\u0007'));
    }
}
