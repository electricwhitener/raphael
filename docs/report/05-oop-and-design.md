# 5. Object-oriented design

Raphael was built for an object-oriented programming course, so its design uses the course's concepts where they fit the problem, not for their own sake. Every class that shows a concept names it in an `// OOP:` comment at the top of its file.

## 5.1 The class hierarchies
Each of the central packages is built around one hierarchy (`→` means "is extended or implemented by"):

```
Structure (abstract)                     what can be drawn
 ├→ ArrayStructure, GridStructure, GenericStructure
 └→ LinkedStructure (abstract)           what lists and trees share
     └→ LinkedListStructure, BinaryTreeStructure, MultiwayTreeStructure

StructureRenderer (abstract)             how each one is drawn
 └→ ArrayRenderer, GridRenderer, LinkedListRenderer,
    BinaryTreeRenderer, MultiwayTreeRenderer, BoxRenderer

HeapObject (abstract)                    an object in the student's memory
 └→ ObjectNode, ArrayNode

Value (interface)                        what a variable holds
 └→ PrimitiveValue, ReferenceValue, NullValue

Tracer (interface)                       "record this program"
 └→ JdiTracer

TraceListener (interface)                "tell me when the step changes"
 └→ CodePanel, VisualPanel, ControlsBar, CallStackPanel, LocalsPanel, ConsolePanel
```

## 5.2 Concepts and where they are used

| Concept | Where | Why it fits |
|---|---|---|
| Encapsulation | `TraceModel`, `Spring`, `Snapshot`, `Trace`, `HeapObject`, `Animator` | Fields are private and changed only through methods that check them. `TraceModel.setStep` keeps the step inside the run, so no panel can be asked to show step −1. |
| Abstraction | `Tracer`, `HeapObject`, `Structure`, `LinkedStructure`, `StructureRenderer` | The window asks a `Tracer` for a `Trace` without knowing about JDI. |
| Interfaces | `Tracer` (with default methods), `Value`, `TraceListener` | A contract with no object behind it; `JdiTracer` and the six panels sign it. |
| Inheritance | the renderers, the structures, `ObjectNode`/`ArrayNode`, the panels (`JPanel`), `MainWindow` (`JFrame`), `OutputDrainer` (`Thread`), `TraceException` (`Exception`), `CodePanel.ColoredView` (Swing's `PlainView`) | Subclasses reuse their parent's fields and methods and add their own. |
| Multilevel inheritance | `Structure` → `LinkedStructure` → `BinaryTreeStructure` | Lists and trees share node handling in the middle class. |
| Polymorphism: overriding | `render()`, `describe()`, `display()`, `getKind()`, `stepChanged()`, `paintComponent()` | `SceneBuilder` calls `render()` without knowing which renderer it has. |
| Polymorphism: overloading | `Tracer.trace(program)` / `trace(program, input)`; `Scene.add(SceneNode)` / `add(SceneEdge)` / `add(Move)` | Same name, different parameters. |
| Composition | `MainWindow` is made of panels; `Animator` holds `Spring`s; `EditorKeys` holds a text area | Built from other objects (has-a) instead of inheriting from them (is-a). |
| Exception handling | `TraceException`; try-with-resources in `SourceCompiler` and `OutputDrainer`; multi-catch in `JdiTracer`; `ExecutionException` in `MainWindow` | A custom checked exception, resources that close themselves, and background errors shown to the user. |
| Threads | `OutputDrainer`, `SwingWorker`, Swing `Timer`s | Output read in parallel, recording in the background, timers for play and animation. |
| Nested and anonymous classes | `Animator.LiveNode`, `JdiTracer.Run`, `CodePanel.LineNumbers`, the `SwingWorker` in `MainWindow.run()` | Helpers that only make sense inside their outer class. |
| Static members | `TraceLimits`, `Style`, `Names`, `NullValue.INSTANCE`, `Diff.between()` | Constants, utility classes, one shared object and a static factory method. |
| Records and enums | `CompileResult`, `SceneNode`, `SceneEdge`, `EndReason`, `SceneNode.Kind` | Small unchangeable data classes, and fixed sets of named values. |
| Packages | `compile`, `trace`, `model`, `detect`, `render`, `ui` | One package per job. |

## 5.3 Examples from the code

**An interface hides the debugger.** The rest of Raphael only knows `Tracer`, so the window would not change if the tracing were done another way. The default methods mean a tracer doesn't have to support stopping, and the two `trace` methods are an overload.

```java
public interface Tracer {
    Trace trace(CompileResult program, String input) throws TraceException;

    default void stop() {
    }

    default int stepsSoFar() {
        return 0;
    }

    default Trace trace(CompileResult program) throws TraceException {
        return trace(program, "");
    }
}
```

**A factory picks the subclass, and polymorphism does the rest.** `forStructure` is the only place that knows which renderer draws which structure. Everyone else calls `render()` on a `StructureRenderer`.

```java
public abstract class StructureRenderer {
    public abstract Dimension render(Scene scene, double x, double y);

    public static StructureRenderer forStructure(Structure structure, RenderContext context) {
        if (structure instanceof GridStructure) {
            return new GridRenderer((GridStructure) structure, context);
        }
        if (structure instanceof ArrayStructure) {
            return new ArrayRenderer((ArrayStructure) structure, context);
        }
        // ... one branch per kind of structure
        return new BoxRenderer((GenericStructure) structure, context);
    }
}
```

**Encapsulation keeps the motion correct.** A `Spring`'s value, speed and target are private, and `step()` is the only way to move it, so no caller can put a shape somewhere the physics wouldn't.

```java
public class Spring {
    private double value;
    private double velocity;
    private double target;

    public void step(double dt, double omega) {
        double a = value - target;
        double b = velocity + omega * a;
        double decay = Math.exp(-omega * dt);
        value = target + (a + b * dt) * decay;
        velocity = (b - omega * (a + b * dt)) * decay;
        // ... settles exactly on the target when close enough
    }
}
```

**A custom exception carries the cause.** `TraceException` extends `Exception`, so it is checked: every caller of `Tracer.trace` must handle it. It keeps the original JDI error as its cause.

```java
public class TraceException extends Exception {
    public TraceException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

## 5.4 Design patterns

| Pattern | Where | What it does here |
|---|---|---|
| Observer | `TraceModel` and `TraceListener` | Panels register with the model and are told when the step changes. They never talk to each other, so adding a panel changes nothing else. |
| Memento | `Trace` and `Snapshot` | Saved, unchangeable copies of the program's state. Stepping back restores an earlier copy instead of re-running. |
| Factory | `StructureRenderer.forStructure()` | One method decides which renderer class to create. |
| Singleton | `NullValue.INSTANCE` | There is only one "null", so one shared object stands for it. |

The Observer pattern is also what makes the views planned in ROADMAP stage 9 cheap to add: each new view (trace table, memory diagram, flowchart) is one more `TraceListener`.
