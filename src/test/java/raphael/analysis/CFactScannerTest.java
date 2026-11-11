package raphael.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

// The facts of C code, read from its tokens.
class CFactScannerTest {

    private static CodeFacts facts(String code) {
        CodeFacts facts = new CodeFacts();
        CFactScanner.scan(facts, code, null, "main");
        return facts;
    }

    @Test
    void functionsTheirLinesParametersAndRecursion() {
        CodeFacts facts = facts("""
                #include <stdio.h>
                #define SQUARE(x) ((x) * (x))
                struct Node { int val; struct Node *next; };
                static int count = 0;

                static int factorial(int n) {
                    if (n <= 1) {
                        return 1;
                    }
                    return n * factorial(n - 1);
                }

                void show(const char *label, int values[], int n) {
                    for (int i = 0; i < n; i++) printf("%s %d\\n", label, values[i]);
                }

                int main(void) {
                    show("f", (int[]) {factorial(3)}, 1);
                    return 0;
                }
                """);
        List<CodeFacts.MethodFact> methods = facts.methods();
        assertEquals(List.of("factorial", "show", "main"), methods.stream().map(CodeFacts.MethodFact::name).toList());
        CodeFacts.MethodFact factorial = methods.get(0);
        assertEquals(6, factorial.firstLine());
        assertEquals(11, factorial.lastLine());
        assertTrue(factorial.recursive());
        assertTrue(factorial.returnsValue());
        assertEquals("main", factorial.className()); // its file's name, as CTracer names frames
        assertEquals(List.of("label", "values", "n"), methods.get(1).parameters());
        assertFalse(methods.get(1).returnsValue());
        assertEquals(List.of(), methods.get(2).parameters()); // (void)
        assertTrue(facts.isRecursive("main", "factorial"));
        assertEquals("n * factorial(n - 1)", facts.returnAt(null, 10).expression());
        assertTrue(facts.returnAt(null, 10).recursive());
    }

    // A struct's fields, so a program with a node struct opens on the picture, as a Java node class does.
    @Test
    void structFields() {
        CodeFacts facts = facts("""
                typedef struct Pair Pair;
                struct Pair { int a, b; Pair *other; };
                struct Node {
                    int key;
                    struct Node *left, *right;
                    struct Node *kids[4];
                    char name[8];
                    int (*compare)(int, int);
                    union { int i; float f; } u;
                };
                typedef struct {
                    double x, y;
                } Point;
                int main(void) { return 0; }
                """);
        java.util.Map<String, String> types = new java.util.LinkedHashMap<>();
        for (CodeFacts.FieldFact field : facts.fields()) {
            types.put(field.className() + "." + field.name(), field.type());
        }
        assertEquals("int", types.get("Pair.a"));
        assertEquals("int", types.get("Pair.b"));
        assertEquals("Pair", types.get("Pair.other")); // a link, though written through the typedef
        assertEquals("int", types.get("Node.key"));
        assertEquals("Node", types.get("Node.left"));
        assertEquals("Node", types.get("Node.right"));
        assertEquals("Node[]", types.get("Node.kids"));
        assertEquals("char[]", types.get("Node.name"));
        assertFalse(types.containsKey("Node.compare")); // a function pointer
        assertEquals("union", types.get("Node.u"));
        assertFalse(types.containsKey("Node.i")); // the union's own fields aren't the struct's
        assertEquals("double", types.get("Point.x"));
        assertEquals("double", types.get("Point.y"));
        assertEquals(List.of("main"), facts.methods().stream().map(CodeFacts.MethodFact::name).toList());
    }

    @Test
    void commentsAndStringsDontCount() {
        CodeFacts facts = facts("""
                /* helper( is not a call, and neither is this: fopen( */
                int helper(int x) {
                    // return helper(x);
                    char *s = "helper(1); fopen(\\"x\\")";
                    char c = '(';
                    return x + 1; // moment: helper adds one
                }
                """);
        assertFalse(facts.methods().get(0).recursive());
        assertFalse(facts.usesFiles());
        assertTrue(facts.calls().isEmpty());
        assertEquals("helper adds one", facts.momentAt(null, 6).text());
    }

    @Test
    void callsKeepTheirArgumentsAndInputAndFilesAreNoticed() {
        CodeFacts facts = facts("""
                #include <stdio.h>
                int main(void) {
                    int a, b;
                    char line[80];
                    scanf("%d, %d", &a, &b);
                    fgets(line, sizeof line, stdin);
                    FILE *f = fopen("out.txt", "w");
                    fprintf(f, "%d\\n", a + b);
                    fclose(f);
                    return 0;
                }
                """);
        CodeFacts.CallFact scanf = facts.callsAt(null, 5).get(0);
        assertEquals("scanf", scanf.method());
        assertEquals(List.of("\"%d, %d\"", "&a", "&b"), scanf.arguments());
        assertEquals(List.of("line", "sizeof line", "stdin"), facts.callsAt(null, 6).get(0).arguments());
        assertTrue(facts.readsInput());
        assertTrue(facts.usesFiles());
    }
}
