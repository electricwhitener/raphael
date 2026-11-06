package raphael.compile;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CompileHintsTest {

    private static void hints(String message, String... parts) {
        String hint = CompileHints.hint(message);
        for (String part : parts) {
            assertTrue(hint != null && hint.contains(part), () -> "hint for \"" + message + "\": " + hint);
        }
    }

    @Test
    void theCommonErrorsGetAPlainHint() {
        hints("';' expected", "missing the ;");
        hints("cannot find symbol\n  symbol:   variable totl\n  location: class Main", "variable totl", "capitals count");
        hints("cannot find symbol\n  symbol:   method sise()\n  location: variable list", "method sise");
        hints("cannot find symbol\n  symbol:   class Scanner\n  location: class Main", "class Scanner", "import");
        hints("incompatible types: possible lossy conversion from double to int", "double is stored in an int", "(int)");
        hints("incompatible types: String cannot be converted to int", "A String is used where a int is needed");
        hints("missing return statement", "return");
        hints("variable x might not have been initialized", "x is used before");
        hints("variable i is already defined in method main(String[])", "i is declared twice");
        hints("reached end of file while parsing", "a } is missing");
        hints("unreported exception java.io.IOException; must be caught or declared to be thrown",
                "java.io.IOException is a checked exception", "throws java.io.IOException");
        hints("non-static variable count cannot be referenced from a static context", "static");
    }

    @Test
    void rareErrorsAreLeftAsTheyAre() {
        assertNull(CompileHints.hint("some message javac rarely writes"));
        assertNull(CompileHints.hint(null));
    }
}
