package raphael.compile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import raphael.model.SourceFile;
import raphael.toolchain.Toolchains;

// C programs compiled with gcc (skipped on a computer without one), and gcc's messages read
// into compile errors (those tests need no gcc).
class CCompilerTest {

    private final CCompiler compiler = new CCompiler();

    @BeforeEach
    void needsGcc(org.junit.jupiter.api.TestInfo test) {
        if (!test.getTags().contains("canned")) {
            Assumptions.assumeTrue(Toolchains.find("gcc") != null, "no gcc on this computer");
        }
    }

    // Runs the compiled program and returns what it printed.
    private static String run(CompileResult program) throws Exception {
        Process p = new ProcessBuilder(program.classDir().resolve(program.mainClass()).toString())
                .redirectErrorStream(true).start();
        p.getOutputStream().close();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(p.waitFor(20, TimeUnit.SECONDS));
        return out.replace("\r\n", "\n");
    }

    @Test
    void compilesAndRuns() throws Exception {
        CompileResult program = compiler.compile("""
                #include <stdio.h>

                int main(void) {
                    int sum = 0;
                    for (int i = 1; i <= 4; i++) sum += i;
                    printf("sum %d\\n", sum);
                    return 0;
                }
                """);
        assertTrue(program.success(), program.errors().toString());
        assertEquals("sum 10\n", run(program));
    }

    // The shim turns stdout's buffer off, so text printed just before a crash isn't lost.
    @Test
    void outputBeforeACrashIsKept() throws Exception {
        CompileResult program = compiler.compile("""
                #include <stdio.h>

                int main(void) {
                    printf("before the crash");
                    int *p = NULL;
                    *p = 1;
                    return 0;
                }
                """);
        assertTrue(program.success(), program.errors().toString());
        assertTrue(run(program).startsWith("before the crash"));
    }

    @Test
    void missingSemicolon() throws Exception {
        CompileResult program = compiler.compile("""
                #include <stdio.h>
                int main(void) {
                    int x = 3
                    printf("%d\\n", x);
                    return 0;
                }
                """);
        assertFalse(program.success());
        CompileResult.CompileError error = program.errors().get(0);
        assertTrue(error.line() == 3 || error.line() == 4, "gcc says 4, clang 3: " + error);
        assertNotNull(GccHints.hint(error.message()), error.message());
        assertEquals(null, error.file()); // one text, as for Java
    }

    // gcc 13 only warns about these; the flags make every gcc stop, as gcc 14 does.
    @Test
    void missingIncludeIsAnErrorWithTheHeaderInTheHint() throws Exception {
        CompileResult program = compiler.compile("""
                int main(void) {
                    printf("hi\\n");
                    return 0;
                }
                """);
        assertFalse(program.success());
        CompileResult.CompileError error = program.errors().get(0);
        assertEquals(2, error.line());
        assertTrue(GccHints.hint(error.message()).contains("#include <stdio.h>"), error.message());
    }

    @Test
    void wrongPointerTypeIsAnError() throws Exception {
        CompileResult program = compiler.compile("""
                int main(void) {
                    int x = 3;
                    char *p = &x;
                    return *p;
                }
                """);
        assertFalse(program.success());
        assertEquals(3, program.errors().get(0).line());
        assertNotNull(GccHints.hint(program.errors().get(0).message()), program.errors().get(0).message());
    }

    @Test
    void undeclaredVariable() throws Exception {
        CompileResult program = compiler.compile("""
                int main(void) {
                    int total = 0;
                    return totl;
                }
                """);
        assertFalse(program.success());
        assertTrue(GccHints.hint(program.errors().get(0).message()).contains("totl"));
    }

    @Test
    void functionNeverWritten() throws Exception {
        CompileResult program = compiler.compile("""
                int helper(int);
                int main(void) { return helper(2); }
                """);
        assertFalse(program.success());
        assertEquals("undefined reference to helper", program.errors().get(0).message());
        assertNotNull(GccHints.hint(program.errors().get(0).message()));
    }

    @Test
    void noMain() throws Exception {
        CompileResult program = compiler.compile("int twice(int x) { return 2 * x; }\n");
        assertFalse(program.success());
        assertEquals("the program has no main function", program.errors().get(0).message());
    }

    private static final SourceFile HEADER = new SourceFile("list.h", """
            struct Node { int val; struct Node *next; };
            struct Node *push(struct Node *head, int val);
            """);
    private static final SourceFile MAIN = new SourceFile("main.c", """
            #include <stdio.h>
            #include "list.h"

            int main(void) {
                struct Node *head = push(push(NULL, 1), 2);
                printf("%d %d\\n", head->val, head->next->val);
                return 0;
            }
            """);

    @Test
    void severalFiles() throws Exception {
        SourceFile list = new SourceFile("list.c", """
                #include <stdlib.h>
                #include "list.h"

                struct Node *push(struct Node *head, int val) {
                    struct Node *n = malloc(sizeof *n);
                    n->val = val;
                    n->next = head;
                    return n;
                }
                """);
        CompileResult program = compiler.compileFolder(List.of(HEADER, list, MAIN), null);
        assertTrue(program.success(), program.errors().toString());
        assertEquals("2 1\n", run(program));
    }

    @Test
    void severalFilesErrorsNameTheirFile() throws Exception {
        SourceFile list = new SourceFile("list.c", """
                #include "list.h"

                struct Node *push(struct Node *head, int val) {
                    return head
                }
                """);
        CompileResult program = compiler.compileFolder(List.of(HEADER, list, MAIN), null);
        assertFalse(program.success());
        assertEquals("list.c", program.errors().get(0).file());
    }

    // ---- gcc's and clang's output, canned ----

    @Test
    @org.junit.jupiter.api.Tag("canned")
    void notesJoinTheirErrorAndWarningsAreLeftOut() {
        List<CompileResult.CompileError> errors = CCompiler.errors("""
                main.c: In function 'main':
                main.c:4:15: warning: unused variable 'y' [-Wunused-variable]
                main.c:4:15: note: a note about the warning
                main.c:2:5: error: implicit declaration of function 'printf' [-Werror=implicit-function-declaration]
                main.c:2:5: note: include '<stdio.h>' or provide a declaration of 'printf'
                cc1: some warnings being treated as errors
                """, false);
        assertEquals(1, errors.size());
        assertEquals(2, errors.get(0).line());
        assertEquals("implicit declaration of function 'printf'\ninclude '<stdio.h>' or provide a declaration of 'printf'",
                errors.get(0).message());
    }

    @Test
    @org.junit.jupiter.api.Tag("canned")
    void gnuLinkerMessages() {
        List<CompileResult.CompileError> errors = CCompiler.errors("""
                /usr/bin/ld: /tmp/ccLZ.o: in function `main':
                /tmp/raphael-c1/src/main.c:2:(.text+0xe): undefined reference to `helper'
                /usr/bin/ld: /tmp/ccC.o: in function `main':
                /tmp/raphael-c1/src/b.c:1: multiple definition of `main'; /tmp/ccK.o:/tmp/raphael-c1/src/a.c:1: first defined here
                /usr/bin/ld: Scrt1.o: in function `_start':
                (.text+0x1b): undefined reference to `main'
                collect2: error: ld returned 1 exit status
                """, true);
        assertEquals(3, errors.size());
        assertEquals(new CompileResult.CompileError("main.c", 2, "undefined reference to helper"), errors.get(0));
        assertEquals(new CompileResult.CompileError("b.c", 0, "main is written more than once"), errors.get(1));
        assertEquals(new CompileResult.CompileError(null, 0, "the program has no main function"), errors.get(2));
    }

    @Test
    @org.junit.jupiter.api.Tag("canned")
    void clangMessages() {
        List<CompileResult.CompileError> errors = CCompiler.errors("""
                main.c:3:14: error: use of undeclared identifier 'totl'
                main.c:2:5: error: call to undeclared function 'printf'; ISO C99 and later do not support implicit function declarations [-Wimplicit-function-declaration]
                main.c:2:5: note: include the header <stdio.h> or explicitly provide a declaration for 'printf'
                Undefined symbols for architecture arm64:
                  "_helper", referenced from:
                      _main in main-1a2b.o
                ld: symbol(s) not found for architecture arm64
                """, false);
        assertEquals(3, errors.size());
        assertTrue(GccHints.hint(errors.get(0).message()).contains("totl"));
        assertTrue(GccHints.hint(errors.get(1).message()).contains("#include <stdio.h>"));
        assertEquals("undefined reference to helper", errors.get(2).message());
    }
}
