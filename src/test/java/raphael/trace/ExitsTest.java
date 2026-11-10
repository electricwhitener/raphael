package raphael.trace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import raphael.compile.CompileResult;
import raphael.compile.SourceCompiler;
import raphael.model.Exit;
import raphael.model.FieldWrite;
import raphael.model.Snapshot;
import raphael.model.Trace;

// What happens inside a line is recorded too: the returns and the field writes since the step before.
class ExitsTest {

    private static Trace trace(String source) throws Exception {
        CompileResult program = new SourceCompiler().compile(source);
        assertTrue(program.success(), () -> program.errors().toString());
        return new JdiTracer().trace(program);
    }

    // In return n * factorial(n - 1) every call returns inside one line; each return is kept, in order.
    @Test
    void everyReturnOfARecursionIsKept() throws Exception {
        Trace trace = trace("""
                public class Factorial {
                    static int factorial(int n) {
                        if (n <= 1) {
                            return 1;
                        }
                        return n * factorial(n - 1);
                    }

                    public static void main(String[] args) {
                        int result = factorial(4);
                        System.out.println(result);
                    }
                }
                """);
        List<String> returned = new ArrayList<>();
        for (Snapshot step : trace.getSnapshots()) {
            for (Exit exit : step.getExits()) {
                returned.add(exit.method() + "=" + (exit.returned() == null ? "void" : exit.returned().display()));
            }
        }
        assertEquals(List.of("factorial=1", "factorial=2", "factorial=6", "factorial=24"), returned);
    }

    // A field write says which object, the value, and the method and line that wrote it.
    @Test
    void aFieldWriteSaysWhoWroteIt() throws Exception {
        Trace trace = trace("""
                public class Main {
                    static class Account {
                        private int balance;

                        void deposit(int amount) {
                            balance = balance + amount;
                        }
                    }

                    public static void main(String[] args) {
                        Account a = new Account();
                        a.deposit(50);
                        System.out.println(a.balance);
                    }
                }
                """);
        FieldWrite found = null;
        Exit constructor = null;
        for (Snapshot step : trace.getSnapshots()) {
            for (FieldWrite write : step.getWrites()) {
                if (write.method().equals("deposit")) {
                    found = write;
                }
            }
            for (Exit exit : step.getExits()) {
                if (exit.method().equals("Account")) {
                    constructor = exit;
                }
            }
        }
        assertEquals("balance", found.field());
        assertEquals("Account", found.className());
        assertEquals("50", found.value().display());
        assertEquals(6, found.line());
        assertTrue(found.objectId() > 0);
        assertNull(constructor.returned(), "a constructor returns nothing");
    }
}
