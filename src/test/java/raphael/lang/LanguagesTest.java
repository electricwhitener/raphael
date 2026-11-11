package raphael.lang;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LanguagesTest {

    // A pasted program is recognised by what it contains.
    @Test
    void pastedCodeIsRecognised() {
        assertEquals(Languages.C, Languages.recognise("#include <stdio.h>\nint main(void) {\n    printf(\"hi\");\n}\n"));
        assertEquals(Languages.CPP, Languages.recognise("#include <iostream>\nusing namespace std;\nint main() { cout << 1; }\n"));
        assertEquals(Languages.PYTHON, Languages.recognise("def f(n):\n    return n\n\nprint(f(3))\n"));
        assertEquals(Languages.JAVA, Languages.recognise("public class A {\n    public static void main(String[] args) {\n"
                + "        System.out.println(1);\n    }\n}\n"));
        assertNull(Languages.recognise("x = 1"), "too little to tell");
    }

    // Files belong to a language by their ending, and get a sensible name.
    @Test
    void endingsAndNames() {
        assertEquals(Languages.C, Languages.forFile("list.c"));
        assertEquals(Languages.C, Languages.forFile("list.H"));
        assertEquals(Languages.CPP, Languages.forFile("tree.hpp"));
        assertEquals(Languages.PYTHON, Languages.forFile("bst.py"));
        assertNull(Languages.forFile("notes.txt"));
        assertEquals("Tree.java", Languages.JAVA.suggestedName("public class Tree {}"));
        assertEquals("main.c", Languages.C.suggestedName("int main(void) {}"));
        assertEquals("tree", Languages.CPP.baseName("tree.cpp"));
        assertEquals(Languages.JAVA, Languages.byId(null), "a recording from before there were languages is Java");
    }

    // Every template has a place where typing starts, and colours as its own language.
    @Test
    void templates() {
        for (Language language : Languages.all()) {
            assertTrue(language.template().lines().anyMatch(line -> !line.isEmpty() && line.isBlank()), language.name());
            assertTrue(language.tokens(language.template()).stream().anyMatch(t -> t.kind() == Token.Kind.KEYWORD), language.name());
        }
        assertTrue(Languages.C.tokens("#include <stdio.h>").get(0).kind() == Token.Kind.KEYWORD, "a directive is coloured");
        assertEquals(Token.Kind.COMMENT, Languages.PYTHON.tokens("# note").get(0).kind());
    }
}
