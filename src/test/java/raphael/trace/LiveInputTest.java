package raphael.trace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import raphael.compile.CompileResult;
import raphael.lang.Language;
import raphael.lang.Languages;
import raphael.model.EndReason;
import raphael.model.Trace;
import raphael.model.TypedLine;

// The terminal's input: lines typed while the program runs reach it, the recording waits (its
// timeout doesn't run out) while the program waits for them, given lines are typed when it asks,
// and Ctrl+D ends the input. For Java, and for C where it can run.
class LiveInputTest {

    // A 1 s timeout: each test waits longer than that before typing.
    private static final RunLimits SHORT = new RunLimits(2000, 1, 256);

    private static final String JAVA_NAME = """
            import java.util.Scanner;
            public class Main {
                public static void main(String[] args) {
                    Scanner in = new Scanner(System.in);
                    System.out.print("name? ");
                    String name = in.nextLine();
                    System.out.println("hi " + name);
                }
            }
            """;

    private static final String C_NAME = """
            #include <stdio.h>
            int main(void) {
                char name[32];
                printf("name? ");
                scanf("%31s", name);
                printf("hi %s\\n", name);
                return 0;
            }
            """;

    private static final String JAVA_SUM = """
            import java.util.Scanner;
            public class Main {
                public static void main(String[] args) {
                    Scanner in = new Scanner(System.in);
                    System.out.print("a? ");
                    int a = in.nextInt();
                    System.out.print("b? ");
                    int b = in.nextInt();
                    System.out.println(a + b);
                }
            }
            """;

    private static final String C_SUM = """
            #include <stdio.h>
            int main(void) {
                int a, b;
                printf("a? ");
                scanf("%d", &a);
                printf("b? ");
                scanf("%d", &b);
                printf("%d\\n", a + b);
                return 0;
            }
            """;

    private static LiveInput input(Language language, String code, String given) {
        String[] lines = code.split("\n");
        return new LiveInput(given, (file, line) -> line <= lines.length && language.readsInputOn(lines[line - 1]));
    }

    private static Trace run(String languageId, String code, LiveInput live, Runnable typist) throws Exception {
        Language language = Languages.byId(languageId);
        Assumptions.assumeTrue(language.cantRun() == null, language.name() + " can't run on this computer");
        CompileResult program = language.compile(code);
        assertTrue(program.success(), program.errors().toString());
        Tracer tracer = language.newTracer();
        tracer.setLimits(SHORT);
        tracer.setLiveInput(live);
        Thread typing = new Thread(typist, "typist");
        typing.start();
        try {
            return tracer.trace(program, "this is ignored: the terminal gives the input");
        } finally {
            typing.join(10_000);
        }
    }

    // Waits until the program waits for input, then twice the timeout more.
    private static void waitForIt(LiveInput live) {
        long until = System.currentTimeMillis() + 60_000;
        while (!live.isWaiting() && System.currentTimeMillis() < until) {
            sleep(20);
        }
        sleep(2000);
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void typedLineWaitsForTheUser(String languageId, String code) throws Exception {
        LiveInput live = input(Languages.byId(languageId), code, "");
        Trace trace = run(languageId, code, live, () -> {
            waitForIt(live);
            live.type("Ada");
        });
        assertEquals(EndReason.FINISHED, trace.getEndReason(), trace.getEndMessage());
        assertEquals("name? hi Ada", trace.getOutput().replace("\r\n", "\n").strip());
        List<TypedLine> typed = trace.getTyped();
        assertEquals(1, typed.size());
        assertEquals("Ada\n", typed.get(0).text());
        assertEquals("name? ".length(), typed.get(0).at(), "the line goes just after the prompt");
        assertTrue(typed.get(0).step() > 0 && typed.get(0).step() < trace.size());
        assertEquals("Ada\n", live.text());
    }

    @Test
    void aJavaProgramWaitsForTheLineTyped() throws Exception {
        typedLineWaitsForTheUser("java", JAVA_NAME);
    }

    @Test
    void aCProgramWaitsForTheLineTyped() throws Exception {
        typedLineWaitsForTheUser("c", C_NAME);
    }

    private static void givenLinesAreTypedWhenAsked(String languageId, String code) throws Exception {
        LiveInput live = input(Languages.byId(languageId), code, "3\n4");
        Trace trace = run(languageId, code, live, () -> { });
        assertEquals(EndReason.FINISHED, trace.getEndReason(), trace.getEndMessage());
        assertEquals("a? b? 7", trace.getOutput().replace("\r\n", "\n").strip());
        List<TypedLine> typed = trace.getTyped();
        assertEquals(List.of("3\n", "4\n"), typed.stream().map(TypedLine::text).toList());
        assertEquals("a? ".length(), typed.get(0).at());
        assertEquals("a? b? ".length(), typed.get(1).at());
        assertTrue(typed.get(0).step() < typed.get(1).step(), "each when the program asked for it");
    }

    @Test
    void anExamplesInputIsTypedForTheUserInJava() throws Exception {
        givenLinesAreTypedWhenAsked("java", JAVA_SUM);
    }

    @Test
    void anExamplesInputIsTypedForTheUserInC() throws Exception {
        givenLinesAreTypedWhenAsked("c", C_SUM);
    }

    @Test
    void endingTheInputEndsTheRead() throws Exception {
        LiveInput live = input(Languages.byId("java"), JAVA_NAME, "");
        Trace trace = run("java", JAVA_NAME, live, () -> {
            waitForIt(live);
            live.end();
        });
        assertEquals(EndReason.EXCEPTION, trace.getEndReason());
        assertTrue(trace.getEndMessage().startsWith("NoSuchElementException"), trace.getEndMessage());
        assertTrue(trace.getTyped().isEmpty());
    }

    @Test
    void endingTheInputGivesCTheEnd() throws Exception {
        String code = """
                #include <stdio.h>
                int main(void) {
                    int n = 7;
                    int got = scanf("%d", &n);
                    printf("%d %d\\n", got, n);
                    return 0;
                }
                """;
        LiveInput live = input(Languages.byId("c"), code, "");
        Trace trace = run("c", code, live, () -> {
            waitForIt(live);
            live.end();
        });
        assertEquals(EndReason.FINISHED, trace.getEndReason(), trace.getEndMessage());
        assertEquals("-1 7", trace.getOutput().strip());
    }

    @Test
    void aSleepingProgramStillTimesOut() throws Exception {
        // Waiting is only waiting on a line that reads: the timeout still ends a program that sleeps.
        String code = """
                import java.util.Scanner;
                public class Main {
                    public static void main(String[] args) throws Exception {
                        Scanner in = new Scanner(System.in);
                        Thread.sleep(8000);
                        System.out.println(in.nextLine());
                    }
                }
                """;
        LiveInput live = input(Languages.byId("java"), code, "");
        Trace trace = run("java", code, live, () -> { });
        assertEquals(EndReason.TIMEOUT, trace.getEndReason(), trace.getEndMessage());
    }
}
