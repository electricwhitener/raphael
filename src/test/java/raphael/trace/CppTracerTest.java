package raphael.trace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import raphael.compile.CompileResult;
import raphael.lang.Languages;
import raphael.model.ArrayNode;
import raphael.model.EndReason;
import raphael.model.HeapObject;
import raphael.model.ObjectNode;
import raphael.model.ReferenceValue;
import raphael.model.Snapshot;
import raphael.model.ThrownException;
import raphael.model.Trace;
import raphael.model.Value;

// C++ programs traced through the same debugger script as C (skipped where C++ can't run).
class CppTracerTest {

    @BeforeEach
    void needsGpp() {
        Assumptions.assumeTrue(Languages.byId("cpp").cantRun() == null, "C++ can't run on this computer");
    }

    private static Trace trace(String code) throws Exception {
        CompileResult program = Languages.byId("cpp").compile(code);
        assertTrue(program.success(), program.errors().toString());
        return Languages.byId("cpp").newTracer().trace(program, "");
    }

    private static List<Integer> lines(Trace trace) {
        List<Integer> lines = new ArrayList<>();
        trace.getSnapshots().forEach(s -> lines.add(s.getLine()));
        return lines;
    }

    private static HeapObject object(Snapshot step, String local) {
        Value v = step.getFrames().get(0).getLocals().get(local);
        return v instanceof ReferenceValue ? step.getObject(((ReferenceValue) v).id()) : null;
    }

    // One step per line, as in Java: a line C++ compiles into several pieces (a map built from
    // a list) is one step; one-line methods and constructors are stepped into; the members C++
    // writes by itself (Shape's constructor, the destructors) aren't.
    @Test
    void eachLineIsOneStepAndOneLineMethodsAreSteppedInto() throws Exception {
        Trace trace = trace("""
                #include <map>
                #include <string>
                struct Shape { virtual ~Shape() {} virtual int sides() const = 0; };
                struct Square : Shape {
                    int w;
                    Square(int w) : w(w) {}
                    int sides() const override { return 4; }
                };
                int main() {
                    std::map<std::string, int> m = {{"a", 1}, {"b", 2}};
                    Shape *s = new Square(3);
                    int n = s->sides();
                    delete s;
                    return n - 4;
                }
                """);
        assertEquals(EndReason.FINISHED, trace.getEndReason(), trace.getEndMessage());
        assertEquals(List.of(10, 11, 6, 12, 7, 13, 3, 14), lines(trace));
    }

    // A pointer to a base class reads as the object's real class, with its base's fields inside it;
    // the standard containers read as arrays of their items, a map's as key=value.
    @Test
    void objectsAndContainersAreReadAsTheyAre() throws Exception {
        Trace trace = trace("""
                #include <map>
                #include <string>
                #include <vector>
                struct Animal { std::string name; virtual ~Animal() {} };
                struct Dog : Animal { int age = 3; };
                int main() {
                    std::vector<int> v = {3, 1, 4};
                    std::map<std::string, int> m = {{"a", 1}};
                    Animal *a = new Dog();
                    a->name = "rex";
                    return 0;
                }
                """);
        Snapshot last = trace.getSnapshots().get(trace.size() - 1);
        ArrayNode v = (ArrayNode) object(last, "v");
        assertEquals("vector<int>", v.getTypeName());
        assertEquals("3 1 4", v.getElements().stream().map(Value::display).reduce((x, y) -> x + " " + y).orElse(""));
        ArrayNode m = (ArrayNode) object(last, "m");
        assertEquals("map<string, int>", m.getTypeName());
        assertEquals("\"a\"=1", m.getElements().get(0).display());
        ObjectNode dog = (ObjectNode) object(last, "a");
        assertEquals("Dog", dog.getTypeName());
        assertEquals(List.of("name", "age"), new ArrayList<>(dog.getFields().keySet()), "the Animal part first, no _vptr");
        assertEquals("\"rex\"", dog.getFields().get("name").display());
    }

    // A throw and its catch: the catch's first line is a step that says what was caught, where
    // it was thrown and which functions it left.
    @Test
    void aCaughtExceptionIsOnTheCatchsFirstStep() throws Exception {
        Trace trace = trace("""
                #include <stdexcept>
                int check(int x) {
                    if (x < 0) throw std::out_of_range("negative");
                    return x;
                }
                int main() {
                    int r = 0;
                    try {
                        r = check(-1);
                    } catch (const std::exception &e) {
                        r = 1;
                    }
                    return r - 1;
                }
                """);
        assertEquals(EndReason.FINISHED, trace.getEndReason(), trace.getEndMessage());
        ThrownException caught = trace.getSnapshots().stream().map(Snapshot::getThrown).filter(t -> t != null).findFirst().orElse(null);
        assertNotNull(caught, "a step says what was caught: " + lines(trace));
        assertEquals("out_of_range", caught.type());
        assertEquals("negative", caught.message());
        assertEquals(3, caught.throwLine());
        assertEquals(10, caught.catchLine());
        assertEquals(List.of("check()"), caught.leftMethods());
        assertTrue(lines(trace).contains(11), "the catch's body runs: " + lines(trace));
    }
}
