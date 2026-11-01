package raphael.compile;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import org.junit.jupiter.api.Test;

class SourceCompilerTest {

    private final SourceCompiler compiler = new SourceCompiler();

    private static final String BODY = "{ public static void main(String[] args) { System.out.println(1); } }";

    private String mainClassOf(String source) throws Exception {
        CompileResult result = compiler.compile(source);
        assertTrue(result.success(), () -> "didn't compile: " + result.errors());
        return result.mainClass();
    }

    @Test
    void aPackageLineIsIgnored() throws Exception {
        assertEquals("Main", mainClassOf("package com.example.dsa;\npublic class Main " + BODY));
    }

    @Test
    void publicFinalAndOtherPublicTypesCompile() throws Exception {
        assertEquals("Solution", mainClassOf("class Node { }\npublic final class Solution " + BODY));
        assertEquals("Solution", mainClassOf("public abstract class Solution " + BODY));
        assertEquals("Solution", mainClassOf("public interface Solution " + BODY));
        assertEquals("Solution", mainClassOf("final public class Solution " + BODY));
    }

    @Test
    void severalPublicClassesCompile() throws Exception {
        assertEquals("Main", mainClassOf("public class Node { }\npublic class Main " + BODY));
    }

    @Test
    void theWordClassInACommentOrStringIsNotAClass() throws Exception {
        assertEquals("Solution", mainClassOf("// public class Wrong\n/* class Wrong2 */\npublic class Solution "
                + "{ static String s = \"public class Wrong3\"; public static void main(String[] a) { } }"));
    }

    @Test
    void aPublicNestedClassIsLeftAlone() throws Exception {
        assertEquals("Main", mainClassOf("class Helper { public static class Inner { public int x; } }\n"
                + "public class Main { public static class Node { public int v; }\n"
                + "public static void main(String[] args) { Node n = new Node(); n.v = 1; } }\n"));
    }

    @Test
    void invisibleCharactersFromAWebPageAreCleanedUp() throws Exception {
        assertEquals("Main", mainClassOf("public class Main " + BODY.replace("{ public", "{ ​public")));
    }

    @Test
    void errorsStillPointAtTheRightLineAfterBlanking() throws Exception {
        CompileResult result = compiler.compile("package a.b;\npublic final class Main {\n    int x = 5\n}\n");
        assertFalse(result.success());
        assertEquals(3, result.errors().get(0).line());
    }

    @Test
    void goodCodeCompiles() throws Exception {
        String source = """
                public class Hello {
                    public static void main(String[] args) {
                        int x = 5;
                        System.out.println(x);
                    }
                }
                """;
        CompileResult result = compiler.compile(source);

        assertTrue(result.success());
        assertTrue(result.errors().isEmpty());
        assertEquals("Hello", result.mainClass());
        assertTrue(Files.exists(result.classDir().resolve("Hello.class")));
    }

    @Test
    void missingSemicolonReportsRightLine() throws Exception {
        String source = """
                public class Broken {
                    public static void main(String[] args) {
                        int x = 5
                        System.out.println(x);
                    }
                }
                """;
        CompileResult result = compiler.compile(source);

        assertFalse(result.success());
        assertEquals(1, result.errors().size());
        assertEquals(3, result.errors().get(0).line());
        assertEquals("';' expected", result.errors().get(0).message());
    }

    @Test
    void findsMainWhenItIsNotInTheFirstClass() throws Exception {
        String source = """
                class Node {
                    int val;
                    Node next;
                }
                class Main {
                    public static void main(String[] args) {
                        Node n = new Node();
                    }
                }
                """;
        CompileResult result = compiler.compile(source);

        assertTrue(result.success());
        assertEquals("Main", result.mainClass());
    }

    @Test
    void reportsMissingMain() throws Exception {
        CompileResult result = compiler.compile("public class NoMain { int x; }");

        assertFalse(result.success());
        assertTrue(result.errors().get(0).message().contains("No main method"));
    }
}
