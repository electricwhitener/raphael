package raphael.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class NamesTest {

    @Test
    void shortNames() {
        assertEquals("String", Names.simple("java.lang.String"));
        assertEquals("Node", Names.simple("Main$Node"));
        assertEquals("Node[]", Names.simple("Main$Node[]"));
        assertEquals("lambda", Names.simple("Main$$Lambda/0x0000760484005000"));
    }
}
