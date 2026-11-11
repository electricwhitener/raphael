package raphael.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import org.junit.jupiter.api.Test;

// Java's null and C's NULL: each says its own word, and each comes back as itself from a recording.
class NullValueTest {

    private static Object roundTrip(Object value) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(value);
        }
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            return in.readObject();
        }
    }

    @Test
    void eachLanguageHasItsWord() throws Exception {
        assertEquals("null", NullValue.INSTANCE.display());
        assertEquals("NULL", NullValue.C.display());
        assertSame(NullValue.INSTANCE, roundTrip(NullValue.INSTANCE));
        assertSame(NullValue.C, roundTrip(NullValue.C));
    }
}
