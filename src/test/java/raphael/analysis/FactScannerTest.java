package raphael.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import raphael.compile.CompileResult;
import raphael.compile.SourceCompiler;

class FactScannerTest {

    private static CodeFacts facts(String source) throws Exception {
        CompileResult result = new SourceCompiler().compile(source);
        assertTrue(result.success(), () -> result.errors().toString());
        return result.facts();
    }

    @Test
    void recursionAndItsBaseCase() throws Exception {
        CodeFacts facts = facts("""
                public class Factorial {
                    static int factorial(int n) {
                        if (n <= 1) {
                            return 1;
                        }
                        return n * factorial(n - 1);
                    }

                    public static void main(String[] args) {
                        System.out.println(factorial(4));
                    }
                }
                """);
        assertTrue(facts.isRecursive("Factorial", "factorial"));
        assertFalse(facts.isRecursive("Factorial", "main"));
        CodeFacts.ReturnFact base = facts.returnAt(null, 4);
        assertEquals("1", base.expression());
        assertFalse(base.recursive(), "the base case returns without calling itself");
        CodeFacts.ReturnFact step = facts.returnAt(null, 6);
        assertEquals("n * factorial(n - 1)", step.expression());
        assertTrue(step.recursive());
        assertEquals("factorial", facts.methodAt(null, 5).name());
        assertTrue(facts.methodAt(null, 5).returnsValue());
    }

    @Test
    void privateFieldsAndAGuardedSetter() throws Exception {
        CodeFacts facts = facts("""
                public class Main {
                    static class Thermostat {
                        private int target = 20;

                        void setTarget(int value) {
                            if (value < 5 || value > 30) {
                                return;
                            }
                            target = value;
                        }
                    }

                    public static void main(String[] args) {
                        new Thermostat().setTarget(24);
                    }
                }
                """);
        assertTrue(facts.field("Thermostat", "target").isPrivate());
        CodeFacts.SetterFact setter = facts.setter("Thermostat", "setTarget");
        assertNotNull(setter);
        assertEquals("target", setter.field());
        assertEquals(6, setter.guardLine(), "the check that can refuse a value");
    }

    @Test
    void aCallThroughAParentTypeAndInheritance() throws Exception {
        CodeFacts facts = facts("""
                public class Main {
                    static abstract class Shape {
                        abstract double area();
                    }

                    static class Circle extends Shape {
                        double area() {
                            return 3;
                        }
                    }

                    public static void main(String[] args) {
                        Shape s = new Circle();
                        System.out.println(s.area());
                    }
                }
                """);
        assertEquals("Shape", facts.superclasses().get("Circle"));
        assertTrue(facts.isAbstract("Shape"));
        CodeFacts.CallFact call = facts.callsAt(null, 14).stream().filter(c -> c.method().equals("area")).findFirst().orElseThrow();
        assertEquals("Shape", call.declaredType(), "s is declared a Shape");
        assertEquals("Shape", call.declaringClass(), "the method named is Shape's; which one runs is the run's business");
    }

    @Test
    void tryCatchFinallyMomentsFilesAndInput() throws Exception {
        CodeFacts facts = facts("""
                import java.io.*;
                import java.util.Scanner;

                public class Main {
                    public static void main(String[] args) {
                        Scanner in = new Scanner(System.in);
                        try {
                            FileReader reader = new FileReader("missing.txt"); // moment: the file isn't there
                        } catch (FileNotFoundException | SecurityException e) {
                            System.out.println("no file");
                        } finally {
                            System.out.println("done");
                        }
                        throw new IllegalStateException("end");
                    }
                }
                """);
        assertTrue(facts.usesFiles());
        assertTrue(facts.readsInput());
        CodeFacts.TryFact tried = facts.tries().get(0);
        assertEquals(7, tried.tryLine());
        assertEquals(List.of("FileNotFoundException", "SecurityException"), tried.catches().get(0).types());
        assertEquals(11, tried.finallyLine());
        assertEquals("the file isn't there", facts.momentAt(null, 8).text());
        assertEquals(List.of(14), facts.throwLines());
    }
}
