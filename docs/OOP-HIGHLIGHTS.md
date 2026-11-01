# Where Raphael uses OOP

This guide shows the main places Raphael uses object-oriented programming. These are highlights, not every use.

**How to read it:**
- All the code is copied from the real files. Each line has a comment above it explaining what it does and which OOP idea it shows. Only bare closing braces have no comment.
- `// ... (some lines skipped)` marks where lines were left out to keep the example short.
- The bold link above each block opens that exact place in the file.
- **Say:** is a one-line explanation you can give out loud.

In VS Code, press **Ctrl+Shift+V** to read this page formatted.

## At a glance

| # | Concept | In short | First example |
|---|---|---|---|
| 1 | [Encapsulation](#1-encapsulation) | Private fields, changed only through methods that check them | [`TraceModel.java:17`](../src/main/java/raphael/ui/TraceModel.java#L17-L97) |
| 2 | [Abstraction with an abstract class](#2-abstraction-with-an-abstract-class) | A partly finished class that says what subclasses must do | [`Structure.java:9`](../src/main/java/raphael/detect/Structure.java#L9-L21) |
| 3 | [Interfaces](#3-interfaces) | A contract with no object behind it; classes sign it with implements | [`Tracer.java:10`](../src/main/java/raphael/trace/Tracer.java#L10-L28) |
| 4 | [Inheritance, including multilevel](#4-inheritance-including-multilevel) | extends: a subclass reuses its parent; here three levels deep | [`LinkedStructure.java:15`](../src/main/java/raphael/detect/LinkedStructure.java#L15-L37) |
| 5 | [Constructor chaining](#5-constructor-chaining) | this(...) and super(...): constructors calling constructors | [`ObjectNode.java:9`](../src/main/java/raphael/model/ObjectNode.java#L9-L21) |
| 6 | [Polymorphism: overriding](#6-polymorphism-overriding) | One call, many behaviours: Java picks the method at run time | [`Value.java:6`](../src/main/java/raphael/model/Value.java#L6-L10) |
| 7 | [Polymorphism: overloading](#7-polymorphism-overloading) | Same name, different parameters; chosen at compile time | [`Scene.java:24`](../src/main/java/raphael/render/Scene.java#L24-L34) |
| 8 | [Keywords: static, final and the private constructor](#8-keywords-static-final-and-the-private-constructor) | Shared, unchangeable, and classes with no objects | [`TraceLimits.java:5`](../src/main/java/raphael/trace/TraceLimits.java#L5-L15) |
| 9 | [Exception handling](#9-exception-handling) | try, catch, finally, throw, throws, custom exceptions | [`TraceException.java:5`](../src/main/java/raphael/trace/TraceException.java#L5-L10) |
| 10 | [Composition](#10-composition) | Objects built from other objects: has-a, not is-a | [`MainWindow.java:66`](../src/main/java/raphael/ui/MainWindow.java#L66-L105) |
| 11 | [Inner classes, anonymous classes and threads](#11-inner-classes-anonymous-classes-and-threads) | Classes inside classes, nameless classes, background work | [`CodePanel.java:354`](../src/main/java/raphael/ui/CodePanel.java#L354-L361) |
| 12 | [Enums and records](#12-enums-and-records) | Fixed sets of values, and tiny data classes | [`EndReason.java:5`](../src/main/java/raphael/model/EndReason.java#L5-L11) |
| 13 | [Design pattern: Observer](#13-design-pattern-observer) | Panels listen to a model, and never talk to each other | [`TraceListener.java:6`](../src/main/java/raphael/ui/TraceListener.java#L6-L9) |
| 14 | [Design patterns: Factory, Singleton and Memento](#14-design-patterns-factory-singleton-and-memento) | Choosing a class, one shared object, saved states | [`StructureRenderer.java:28`](../src/main/java/raphael/render/StructureRenderer.java#L28-L45) |

## How the classes fit together

The inheritance at the centre of Raphael. `→` means "is extended by". The `model`, `detect` and `render` packages are each built around one of these.

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

## 1. Encapsulation

Encapsulation keeps an object's data `private` and lets other classes change it only through methods, which can check the change first. In C, any code holding a `struct` can write any field. Here, it can't.

`TraceModel` holds which step is showing. If any panel could write `step` directly, it could set it to -1 and crash every other panel.

**[`ui/TraceModel.java`, lines 17–97](../src/main/java/raphael/ui/TraceModel.java#L17-L97)**

```java
// `private`: only code inside this class can touch this field.
private Trace trace;
// ... (some lines skipped)
// The current step is private too, so nothing outside can set it to a bad value.
private int step;
// ... (some lines skipped)
// A getter: other classes can read the step, but this gives them a copy of the number.
public int getStep() {
    // Hands back the value. The field itself stays hidden.
    return step;
}
// ... (some lines skipped)
// The only way to change the step. It's public, but it controls what happens.
public void setStep(int newStep) {
    // If no program has been run, there are no steps to move to.
    if (!hasSteps()) {
        // So it refuses, and nothing changes.
        return;
    }
    // Forces the number inside the valid range, so a bad step is impossible.
    int clamped = Math.max(0, Math.min(newStep, trace.size() - 1));
    // ... (some lines skipped)
    // Only now is the private field changed.
    step = clamped;
```

**Say:** "The fields are private and setStep() is the only way to change the step. It clamps the value, so no panel can ever see a step that doesn't exist."

**Also used in:** `Spring` (position and speed can only change through `step()`), `Snapshot`, `Animator`, and most model classes.

## 2. Abstraction with an abstract class

Abstraction means showing what something does and hiding how. An `abstract` class is a partly finished class: it says what every subclass must be able to do, and you can't create an object of it directly.

`Structure` is the idea of "something Raphael can draw". There's no such thing as a plain Structure, only an array, a grid, a tree and so on.

**[`detect/Structure.java`, lines 9–21](../src/main/java/raphael/detect/Structure.java#L9-L21)**

```java
// `abstract class`: `new Structure()` won't compile. Only its subclasses can be created.
public abstract class Structure {
    // An abstract method has no body. Every subclass must write its own version.
    public abstract Set<Long> getObjectIds();
    // Another one: each kind of structure must say what kind it is.
    public abstract String getKind();
    // A normal method with a body. Abstract classes can have these, and every subclass
    // inherits it.
    public boolean contains(long id) {
        // It calls the abstract method above, trusting that each subclass has written it.
        return getObjectIds().contains(id);
    }
}
```

**Say:** "Structure says what every drawable thing must provide, without saying how. The rest of the program works with Structure and doesn't need to know which kind it has."

**Also used in:** `HeapObject` (`ObjectNode` or `ArrayNode`), `StructureRenderer` (six renderers), `LinkedStructure`.

## 3. Interfaces

An interface is a list of methods with no object behind it: a contract. A class signs the contract with `implements`. It's like a C header file that many different `.c` files could implement.

The window needs "something that records a program". It shouldn't care that this is done with JDI, so that's hidden behind an interface.

**The contract:**

**[`trace/Tracer.java`, lines 10–28](../src/main/java/raphael/trace/Tracer.java#L10-L28)**

```java
// `interface`: says what a Tracer can do, not how.
public interface Tracer {
    // No body. Any class that implements Tracer must write this method.
    Trace trace(CompileResult program, String input) throws TraceException;
    // `default`: an interface method with a body. A Tracer gets this for free and may override
    // it.
    default void stop() {
    }
    // A second default method: by default, no steps have been recorded.
    default int stepsSoFar() {
        // Used if a class doesn't write its own version.
        return 0;
    }
    // Overloading: same name as the first trace method, different parameters. For programs
    // with no input.
    default Trace trace(CompileResult program) throws TraceException {
        // It reuses the other version, with empty input.
        return trace(program, "");
    }
}
```

**The class that signs it:**

**[`trace/JdiTracer.java`, lines 47–56](../src/main/java/raphael/trace/JdiTracer.java#L47-L56)**

```java
// `implements`: JdiTracer promises to provide everything Tracer lists.
public class JdiTracer implements Tracer {
    // ... (some lines skipped)
    // `@Override`: tells the compiler this fills in the interface's method. A typo here
    // becomes a compile error.
    @Override
    // The real work, using JDI. The rest of the app never sees these details.
    public Trace trace(CompileResult program, String input) throws TraceException {
```

**The class that uses it:**

**[`ui/MainWindow.java`, line 115](../src/main/java/raphael/ui/MainWindow.java#L115-L115)**

```java
// The variable's type is the interface, and the object is a JdiTracer. The window only ever
// calls Tracer's methods.
private final Tracer tracer = new JdiTracer();
```

**Say:** "MainWindow only knows the Tracer interface. I could write a different tracer, and this is the only line in the window that would change."

**Also used in:** `Value` (three kinds of value, see section 6), `TraceListener` (six panels, see section 13).

## 4. Inheritance, including multilevel

A subclass gets the fields and methods of its parent with `extends`, and adds its own. This avoids writing the same code twice. Raphael has three levels here: `Structure` → `LinkedStructure` → `BinaryTreeStructure`.

`LinkedStructure` holds what linked lists and trees have in common. `BinaryTreeStructure` adds only what's special about a binary tree.

**The middle level:**

**[`detect/LinkedStructure.java`, lines 15–37](../src/main/java/raphael/detect/LinkedStructure.java#L15-L37)**

```java
// It extends Structure, and is still abstract, because it doesn't answer getKind().
public abstract class LinkedStructure extends Structure {
    // Lists and trees both have nodes, so the field lives here, once.
    private final Map<Long, ObjectNode> nodes;
    // `protected`: subclasses can call this constructor, but outside classes can't.
    protected LinkedStructure(Map<Long, ObjectNode> nodes) {
        // Keeps a read-only copy, so nothing outside can change the nodes (encapsulation
        // again).
        this.nodes = Collections.unmodifiableMap(new LinkedHashMap<>(nodes));
    }
    // ... (some lines skipped)
    // `protected` helper shared by lists and trees: follows a link from one node to the next.
    protected long follow(long id, String field) {
```

**The bottom level:**

**[`detect/BinaryTreeStructure.java`, lines 8–36](../src/main/java/raphael/detect/BinaryTreeStructure.java#L8-L36)**

```java
// Inherits everything above. Only what's special about a binary tree is written here.
public class BinaryTreeStructure extends LinkedStructure {
    // A tree has a root, which a list doesn't need.
    private final long root;
    // ... (some lines skipped)
    // The constructor takes the parent's data as well as its own.
    public BinaryTreeStructure(Map<Long, ObjectNode> nodes, long root, String leftField, String rightField) {
        // `super(...)`: runs the parent's constructor first, to set up the inherited part.
        super(nodes);
        // `this.root` is the field, and plain `root` is the parameter. `this` tells them
        // apart.
        this.root = root;
    // ... (some lines skipped)
    }
    // ... (some lines skipped)
    // A new method that only trees have.
    public long getLeft(long id) {
        // It calls follow(), which it inherited, so it needn't be written twice.
        return follow(id, leftField);
    }
    // ... (some lines skipped)
    // Fills in the abstract method from Structure, at the top of the chain.
    @Override
    // Finally, this class is concrete: every abstract method now has a body.
    public String getKind() {
        // The answer for this kind of structure.
        return "binary tree";
    }
```

**Say:** "Structure, LinkedStructure and BinaryTreeStructure form a multilevel hierarchy. follow() is written once, in the middle class, and both lists and trees inherit it."

**Also used in:** `MainWindow extends JFrame`, panels `extends JPanel`, `OutputDrainer extends Thread`, `TraceException extends Exception`.

## 5. Constructor chaining

When one constructor calls another. `this(...)` calls another constructor in the same class, and `super(...)` calls the parent's. Either must be the first line, because an object has to be built from the parent down.

Creating an `ObjectNode` with four arguments runs through three constructors.

**In the subclass:**

**[`model/ObjectNode.java`, lines 9–21](../src/main/java/raphael/model/ObjectNode.java#L9-L21)**

```java
// ObjectNode is a kind of HeapObject.
public class ObjectNode extends HeapObject {
    // ... (some lines skipped)
    // Constructor 1, the short form: for an object with no parent class of the student's.
    public ObjectNode(long id, String typeName, Map<String, Value> fields, Map<String, String> fieldTypes) {
        // `this(...)`: passes everything to constructor 2, with null for the parent, so the
        // setup code exists only once.
        this(id, typeName, fields, fieldTypes, null);
    }
    // Constructor 2, the full form. Two constructors with different parameters is overloading.
    public ObjectNode(long id, String typeName, Map<String, Value> fields, Map<String, String> fieldTypes, String parentName) {
        // `super(...)`: goes up to HeapObject's constructor. It has to come first.
        super(id, typeName);
        // Then this class sets up its own part.
        this.parentName = parentName;
```

**In the parent:**

**[`model/HeapObject.java`, lines 6–14](../src/main/java/raphael/model/HeapObject.java#L6-L14)**

```java
// `abstract`: a HeapObject only ever exists as part of a subclass, like ObjectNode.
public abstract class HeapObject {
    // ... (some lines skipped)
    // `protected`: visible to subclasses, which is exactly who needs it.
    protected HeapObject(long id, String typeName) {
        // Every object in memory has an id, set here, once, for all subclasses.
        this.id = id;
        // And a type name.
        this.typeName = typeName;
    }
```

**Say:** "new ObjectNode with four arguments goes this(...) to the five-argument constructor, then super(...) to HeapObject. The parent part is built first, and no setup code is repeated."

**Also used in:** `Animator(Runnable)` calls `this(onFrame, true)`. `MainWindow` calls `super("raphael")`, which sets the title in `JFrame`.

## 6. Polymorphism: overriding

Polymorphism means "many forms". The same method call does different things depending on the object's real class, and Java decides which while the program runs. Each class overrides the method with its own version.

A value in the student's program can be a number, an arrow or null. Each one knows how to show itself.

**One contract:**

**[`model/Value.java`, lines 6–10](../src/main/java/raphael/model/Value.java#L6-L10)**

```java
// Everything held in a variable is a Value.
public interface Value {
    // Each kind must say how to display itself.
    String display();
}
```

**Form 1, an arrow to an object:**

**[`model/ReferenceValue.java`, lines 5–11](../src/main/java/raphael/model/ReferenceValue.java#L5-L11)**

```java
// A record (see section 12) that implements Value.
public record ReferenceValue(long id) implements Value {
    // Overrides display() for this kind.
    @Override
    // Same method name as the others.
    public String display() {
        // Different result: "#12".
        return "#" + id;
    }
}
```

**Form 2, null:**

**[`model/NullValue.java`, lines 5–15](../src/main/java/raphael/model/NullValue.java#L5-L15)**

```java
// Another class that implements the same interface.
public final class NullValue implements Value {
    // ... (some lines skipped)
    // Overrides display() for this kind.
    @Override
    // Same method name again.
    public String display() {
        // Different result again.
        return "null";
    }
```

**The bigger example.** `SceneBuilder` draws every structure without knowing what kind it has:

**[`render/SceneBuilder.java`, lines 122–135](../src/main/java/raphael/render/SceneBuilder.java#L122-L135)**

```java
// The variable's type is the abstract parent. The object could be any of the six renderers.
StructureRenderer renderer = StructureRenderer.forStructure(structure, context);
// ... (some lines skipped)
// One line of code. A tree renderer lays out a tree, and an array renderer a row of cells.
// Java picks at run time.
Dimension size = renderer.render(scene, rowX, y);
```

**Say:** "The line renderer.render(...) never changes, yet it can draw six different things, because each subclass overrides render(). To add a new structure, I'd add a subclass, and this line wouldn't change."

**Also used in:** `HeapObject.describe()`, `Structure.getKind()`, `TraceListener.stepChanged()`, `paintComponent()`, `CodePanel.ColoredView.drawUnselectedText()`.

## 7. Polymorphism: overloading

Several methods with the same name and different parameters. The compiler picks one from the arguments, before the program runs. That's the difference from overriding, which is decided while it runs.

**[`render/Scene.java`, lines 24–34](../src/main/java/raphael/render/Scene.java#L24-L34)**

```java
// Adding a shape.
public void add(SceneNode node) {
    // Shapes go in the shapes map.
    nodes.put(node.key(), node);
}
// Same name, but this one takes an arrow.
public void add(SceneEdge edge) {
    // Arrows go in a different map.
    edges.put(edge.key(), edge);
}
// Same name, third version, for a value copied between cells.
public void add(Move move) {
    // Moves go in a list.
    moves.add(move);
}
```

**Say:** "Callers just say scene.add(something), and the compiler picks the right version from the type of the argument."

**Also used in:** `Tracer.trace()`, `App.setupLook()`, and the constructors in section 5.

## 8. Keywords: static, final and the private constructor

- `static` belongs to the class itself, with one copy shared by everything. It's used without creating an object.
- `final` means it can't change. On a variable it's a constant. On a class it means nothing can extend it.
- A `private` constructor stops anyone creating objects.

**A class of constants:**

**[`trace/TraceLimits.java`, lines 5–15](../src/main/java/raphael/trace/TraceLimits.java#L5-L15)**

```java
// `final`: no class can extend this one.
public final class TraceLimits {
    // `public static final`: a constant, shared, used as TraceLimits.MAX_STEPS. It can't be
    // changed by mistake.
    public static final int MAX_STEPS = 2000;
    // ... (some lines skipped)
    // `private` constructor: `new TraceLimits()` won't compile, because an object here would
    // mean nothing.
    private TraceLimits() {
    }
}
```

**A static factory method:**

**[`render/Diff.java`, lines 21–25](../src/main/java/raphael/render/Diff.java#L21-L25)**

```java
// Private, so the only way to get a Diff is through the method below.
private Diff() {
}
// `static`: called on the class, as Diff.between(a, b), with no object needed first.
public static Diff between(Scene before, Scene after) {
    // It can use the private constructor, because it's inside the class.
    Diff diff = new Diff();
```

**Say:** "TraceLimits holds constants, so it's final with a private constructor, and nobody can create or change it."

**Also used in:** `Style`, `Names`, `Section` and `JavaTokens` are similar. See the keyword table for more.

## 9. Exception handling

An exception is Java's way of reporting an error without crashing, by jumping to code that can deal with it. In C you'd check a return code after every call. Raphael uses several techniques, shown here from the lowest level up to the window.

**A custom exception.** It extends `Exception`, so the compiler makes callers handle it (a *checked* exception):

**[`trace/TraceException.java`, lines 5–10](../src/main/java/raphael/trace/TraceException.java#L5-L10)**

```java
// Inheritance: our own error type, so callers can catch exactly this problem.
public class TraceException extends Exception {
    // Takes our message, and the original error that caused it.
    public TraceException(String message, Throwable cause) {
        // Hands both to Exception's constructor.
        super(message, cause);
    }
}
```

**`throws`, `try`, multi-`catch` and `throw`,** turning low-level errors into our own:

**[`trace/JdiTracer.java`, lines 91–103](../src/main/java/raphael/trace/JdiTracer.java#L91-L103)**

```java
// `throws`: warns callers that this method can fail, and that they must handle it.
private VirtualMachine launch(CompileResult program) throws TraceException {
    // ... (some lines skipped)
    // `try`: the code that might fail.
    try {
        // Starting a second JVM can go wrong in several ways.
        return connector.launch(arguments);
    // Multi-catch: one block for three kinds of error.
    } catch (IOException | IllegalConnectorArgumentsException | VMStartException e) {
        // `throw`: reports it as our own type, with a clear message, and keeps the original
        // inside.
        throw new TraceException("Could not start the program: " + e.getMessage(), e);
    }
}
```

**`finally`,** code that runs whether or not there was an error:

**[`trace/JdiTracer.java`, lines 58–63](../src/main/java/raphael/trace/JdiTracer.java#L58-L63)**

```java
// Remembers the run in progress.
current = new Run(vm);
// The recording might fail.
try {
    // Records the program and returns the result.
    return current.record(input);
// `finally` still runs after that return, and also after an exception.
} finally {
    // So the run is always cleared, however it ended.
    current = null;
}
```

**try-with-resources,** which closes a file or stream automatically:

**[`trace/OutputDrainer.java`, lines 26–34](../src/main/java/raphael/trace/OutputDrainer.java#L26-L34)**

```java
// Anything opened in these brackets is closed automatically at the end, even after an error.
// In C you'd have to remember fclose().
try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
    // A small buffer to read into.
    char[] buffer = new char[1024];
    // Holds how many characters each read returned.
    int count;
    // Keeps reading what the student's program prints, until it ends.
    while ((count = reader.read(buffer)) != -1) {
        // Stores it for the console panel.
        out.append(buffer, 0, count);
    }
// When the program ends, the stream can fail. That's expected here, so it's simply caught.
} catch (IOException e) {
}
```

**Where the error finally reaches the user:**

**[`ui/MainWindow.java`, lines 488–495](../src/main/java/raphael/ui/MainWindow.java#L488-L495)**

```java
// The background work might have failed.
try {
    // get() returns the result, or throws if the background thread failed.
    show(get());
// An error on the other thread arrives wrapped in an ExecutionException.
} catch (ExecutionException e) {
    // Shows the real cause in the window, instead of crashing the app.
    say("[error] could not run the program: " + e.getCause().getMessage(), Style.ERROR);
    // And in the console.
    console.showMessage(String.valueOf(e.getCause()));
// A second catch, for a different error.
} catch (InterruptedException e) {
    // Keeps the thread's "interrupted" flag set for whoever needs it.
    Thread.currentThread().interrupt();
}
```

**Say:** "The low-level JDI errors become one TraceException with a clear message. It travels up with throws, and MainWindow catches it and shows it to the user, so the app never crashes because of a student's program."

**Also used in:** `TraceModel.setSpeed()` throws `IllegalArgumentException` for a bad speed (an *unchecked* exception). `SourceCompiler` uses try-with-resources too.

## 10. Composition

Composition means an object is built from other objects: "has a" instead of "is a". `MainWindow` *is a* window (inheritance) and *has a* code panel (composition). This keeps each class small, and each does one job.

**The window is made of parts:**

**[`ui/MainWindow.java`, lines 66–105](../src/main/java/raphael/ui/MainWindow.java#L66-L105)**

```java
// Inheritance: MainWindow is a JFrame, so it gets everything a window can do.
public class MainWindow extends JFrame {
    // ... (some lines skipped)
    // Composition: it has a model that holds the current step.
    private final TraceModel model = new TraceModel();
    // It has a code editor, and gives it the model.
    private final CodePanel code = new CodePanel(model);
    // It has the picture. Each part is its own class with one job.
    private final VisualPanel visual = new VisualPanel(model);
```

**The same idea in a small class:**

**[`ui/EditorKeys.java`, lines 16–24](../src/main/java/raphael/ui/EditorKeys.java#L16-L24)**

```java
// Adds keyboard features to a text box, but isn't a kind of text box.
public class EditorKeys {
    // ... (some lines skipped)
    // It holds a text area instead of extending one.
    private final JTextArea editor;
    // It's given the text area when it's created.
    public EditorKeys(JTextArea editor) {
        // And keeps it, to work on later.
        this.editor = editor;
    }
```

**Say:** "MainWindow is-a JFrame through inheritance, but has-a CodePanel through composition. EditorKeys holds a text area rather than extending one, because adding key shortcuts doesn't make it a new kind of text area."

**Also used in:** `Animator`'s live shapes each hold `Spring`s. `CodePanel` holds an `EditorKeys`.

## 11. Inner classes, anonymous classes and threads

A class can be defined inside another when only that class needs it. An *anonymous* class is written and used on the spot, with no name. Threads let two things happen at once: Raphael does slow work on a background thread, so the window never freezes.

**A nested class that overrides Swing's drawing,** which is how the code colours work:

**[`ui/CodePanel.java`, lines 354–361](../src/main/java/raphael/ui/CodePanel.java#L354-L361)**

```java
// Defined inside CodePanel. It extends a Swing class that draws one line of text.
private final class ColoredView extends PlainView {
    // Its constructor.
    ColoredView(Element element) {
        // Passes the element up to PlainView's constructor.
        super(element);
    }
    // Replaces how Swing draws a line of text, without changing any Swing code.
    @Override
    // Swing calls this for each line. Now each piece is drawn in its own colour.
    protected float drawUnselectedText(Graphics2D g, float x, float y, int start, int end) throws BadLocationException {
```

**An anonymous class:**

**[`ui/MainWindow.java`, lines 464–497](../src/main/java/raphael/ui/MainWindow.java#L464-L497)**

```java
// `new SwingWorker...() {`: creates a nameless subclass of SwingWorker, right here.
new SwingWorker<RunResult, String>() {
    // Overrides SwingWorker's method.
    @Override
    // This runs on a background thread, so the window doesn't freeze while it works.
    protected RunResult doInBackground() throws Exception {
        // Compiling can be slow, so it happens here.
        CompileResult program = new SourceCompiler().compile(source);
    // ... (some lines skipped)
    // Overrides another method. This one runs back on the window's thread.
    @Override
    // ... (some lines skipped)
    // Called when the background work is finished.
    protected void done() {
// ... (some lines skipped)
// Starts the worker.
}.execute();
```

**A thread made with inheritance:**

**[`trace/OutputDrainer.java`, lines 12–25](../src/main/java/raphael/trace/OutputDrainer.java#L12-L25)**

```java
// `extends Thread`: this class is a thread.
public class OutputDrainer extends Thread {
    // ... (some lines skipped)
    // `run()` is what the new thread does. Thread's version does nothing, so this overrides
    // it.
    @Override
    // Started with start(), which calls this on a new thread. Without it, the student's
    // program would freeze when its output pipe filled up.
    public void run() {
```

**Say:** "The slow work runs in an anonymous SwingWorker on a background thread, and OutputDrainer is a thread that reads the program's output. That's why the window never freezes."

**Also used in:** `JdiTracer.Run`, `Animator.LiveNode`, `Section.Hairline`, and many lambdas like `e -> run()`.

## 12. Enums and records

An `enum` is a fixed list of named values, so a typo is a compile error, where a plain number or string wouldn't be. A `record` is a short way to write a small class that only holds data, like a C `struct`, except that its fields can't change.

**An enum:**

**[`model/EndReason.java`, lines 5–11](../src/main/java/raphael/model/EndReason.java#L5-L11)**

```java
// Every way a run can end. There can be no other values.
public enum EndReason {
    // The program ended normally.
    FINISHED,
    // It took too many steps.
    STEP_LIMIT,
    // It crashed.
    EXCEPTION,
    // It stopped responding.
    TIMEOUT,
    // The user pressed stop.
    STOPPED
}
```

**A record, with another nested inside:**

**[`compile/CompileResult.java`, lines 10–14](../src/main/java/raphael/compile/CompileResult.java#L10-L14)**

```java
// Java writes the private final fields, the constructor and the getters from this one line.
public record CompileResult(boolean success, List<CompileError> errors, Path classDir, String mainClass) {
    // A small record inside: one compile error, with its line and message.
    public record CompileError(long line, String message) {}
}
```

**Say:** "EndReason means a run's ending can only be one of five things, checked by the compiler. CompileResult is a record, which gives me encapsulated, unchangeable data in one line."

**Also used in:** `SceneNode` and `SceneEdge` are records with nested enums. `RenderContext.Marker` and `IndexUsage.Access` are records too.

## 13. Design pattern: Observer

A design pattern is a well-known solution to a common problem. Here the problem is: when the step changes, six panels must update, but they shouldn't know about each other. So they all listen to the model, which tells them when something changes.

**The contract for listeners:**

**[`ui/TraceListener.java`, lines 6–9](../src/main/java/raphael/ui/TraceListener.java#L6-L9)**

```java
// Anything that wants to hear about step changes signs this.
public interface TraceListener {
    // The model calls this whenever the step changes.
    void stepChanged(TraceModel model);
```

**The model keeps a list and tells everyone:**

**[`ui/TraceModel.java`, lines 22–100](../src/main/java/raphael/ui/TraceModel.java#L22-L100)**

```java
// Generics: a list that can only hold TraceListeners. It doesn't know which panels they are.
private final List<TraceListener> listeners = new ArrayList<>();
// A panel signs up here.
public void addListener(TraceListener listener) {
    // It's added to the list.
    listeners.add(listener);
}
    // ... (some lines skipped)
    // After a real change of step...
    step = clamped;
    // ...it goes through everyone on the list...
    for (TraceListener listener : listeners) {
        // ...and tells each one. Polymorphism: each panel runs its own version.
        listener.stepChanged(this);
    }
```

**One of the listeners:**

**[`ui/CallStackPanel.java`, lines 20–46](../src/main/java/raphael/ui/CallStackPanel.java#L20-L46)**

```java
// Inherits from JPanel and implements the contract: a class can do both.
public class CallStackPanel extends JPanel implements TraceListener {
            // ... (some lines skipped)
            // Its own version of stepChanged.
            @Override
    // ... (some lines skipped)
    // This panel's reaction to a step change.
    public void stepChanged(TraceModel model) {
        // It clears the old list of methods, then fills it in again below.
        frames.clear();
```

**Connecting them:**

**[`ui/MainWindow.java`, lines 124–125](../src/main/java/raphael/ui/MainWindow.java#L124-L125)**

```java
// Each panel signs up with the model. This is the only place that knows about all of them.
model.addListener(code);
// The picture signs up the same way.
model.addListener(visual);
```

**Say:** "A button only calls model.setStep(). The model tells every panel. No panel knows any other panel exists, so I can add a new panel without changing the others."

## 14. Design patterns: Factory, Singleton and Memento

Three more that appear naturally in Raphael.

**Factory:** one method decides which class to create, so nobody else has to.

**[`render/StructureRenderer.java`, lines 28–45](../src/main/java/raphael/render/StructureRenderer.java#L28-L45)**

```java
// `static`, and it returns the abstract parent type. Callers don't know which subclass they'll
// get.
public static StructureRenderer forStructure(Structure structure, RenderContext context) {
    // `instanceof`: checks what kind of object this really is.
    if (structure instanceof GridStructure) {
        // Creates the matching renderer. The cast tells Java to treat it as a GridStructure.
        return new GridRenderer((GridStructure) structure, context);
    }
    // ... (some lines skipped)
    // The same for a binary tree.
    if (structure instanceof BinaryTreeStructure) {
        // Creates that renderer instead.
        return new BinaryTreeRenderer((BinaryTreeStructure) structure, context);
    }
    // ... (some lines skipped)
    // Anything else is drawn as a plain box.
    return new BoxRenderer((GenericStructure) structure, context);
}
```

**Singleton:** only one object of a class ever exists.

**[`model/NullValue.java`, lines 5–10](../src/main/java/raphael/model/NullValue.java#L5-L10)**

```java
// `final`: nothing can extend it.
public final class NullValue implements Value {
    // The one and only object, created once and shared by everyone.
    public static final NullValue INSTANCE = new NullValue();
    // `private`: nobody else can create another.
    private NullValue() {
    }
```

**Memento:** saved copies of an object's state, so you can go back to one. That's how stepping backwards works: the program never runs backwards.

**[`model/Snapshot.java`, lines 12–22](../src/main/java/raphael/model/Snapshot.java#L12-L22)**

```java
// One saved moment of the student's program.
public class Snapshot {
    // `final`: can't be changed after it's made, so a saved moment stays true.
    private final List<FrameSnapshot> frames;
        // ... (some lines skipped)
        // List.copyOf makes a read-only copy, so nothing outside can change it later.
        this.frames = List.copyOf(frames);
```

**Say:** "The Factory hides which renderer gets created. The Singleton means all nulls are one shared object. And each Snapshot is a Memento, so going back a step just means showing an earlier one."

## Keywords, with one real example each

| Keyword | What it means | Where |
|---|---|---|
| `class`, `new` | Defines a type; creates an object | [`MainWindow.java:103`](../src/main/java/raphael/ui/MainWindow.java#L103) |
| `this` | This object; tells a field from a parameter | [`BinaryTreeStructure.java:16`](../src/main/java/raphael/detect/BinaryTreeStructure.java#L16) |
| `this(...)` | Calls another constructor in this class | [`ObjectNode.java:16`](../src/main/java/raphael/model/ObjectNode.java#L16) |
| `super(...)` | Calls the parent's constructor | [`ObjectNode.java:20`](../src/main/java/raphael/model/ObjectNode.java#L20) |
| `extends` | Inherits from a class | [`BinaryTreeStructure.java:8`](../src/main/java/raphael/detect/BinaryTreeStructure.java#L8) |
| `implements` | Signs an interface | [`JdiTracer.java:47`](../src/main/java/raphael/trace/JdiTracer.java#L47) |
| `abstract` | Unfinished; subclasses must complete it | [`Structure.java:15`](../src/main/java/raphael/detect/Structure.java#L15) |
| `interface` | A contract with no object behind it | [`Tracer.java:10`](../src/main/java/raphael/trace/Tracer.java#L10) |
| `default` | An interface method with a body | [`Tracer.java:16`](../src/main/java/raphael/trace/Tracer.java#L16) |
| `@Override` | Asks the compiler to check that this really overrides something | [`OutputDrainer.java:24`](../src/main/java/raphael/trace/OutputDrainer.java#L24) |
| `private` | Only this class can use it | [`TraceModel.java:20`](../src/main/java/raphael/ui/TraceModel.java#L20) |
| `protected` | This class and its subclasses | [`HeapObject.java:11`](../src/main/java/raphael/model/HeapObject.java#L11) |
| `public` | Anyone can use it | [`TraceModel.java:64`](../src/main/java/raphael/ui/TraceModel.java#L64) |
| `static` | Belongs to the class, not to an object | [`Diff.java:24`](../src/main/java/raphael/render/Diff.java#L24) |
| `final` | Can't change, or can't be extended | [`TraceLimits.java:7`](../src/main/java/raphael/trace/TraceLimits.java#L7) |
| `instanceof` | Checks what kind of object this really is | [`StructureRenderer.java:29`](../src/main/java/raphael/render/StructureRenderer.java#L29) |
| `enum` | A fixed set of named values | [`EndReason.java:5`](../src/main/java/raphael/model/EndReason.java#L5) |
| `record` | A tiny class that only holds data | [`ReferenceValue.java:5`](../src/main/java/raphael/model/ReferenceValue.java#L5) |
| `try`, `catch` | Runs risky code and handles the error | [`JdiTracer.java:100`](../src/main/java/raphael/trace/JdiTracer.java#L100) |
| `finally` | Always runs, error or not | [`JdiTracer.java:61`](../src/main/java/raphael/trace/JdiTracer.java#L61) |
| `throw` | Reports an error | [`JdiTracer.java:101`](../src/main/java/raphael/trace/JdiTracer.java#L101) |
| `throws` | Warns that a method can fail | [`Tracer.java:13`](../src/main/java/raphael/trace/Tracer.java#L13) |
| `volatile` | A field shared safely between threads | [`JdiTracer.java:53`](../src/main/java/raphael/trace/JdiTracer.java#L53) |
| Generics `<T>` | A collection that only holds one type | [`TraceModel.java:22`](../src/main/java/raphael/ui/TraceModel.java#L22) |

## A suggested order for showing this

1. **Run the program first.** Show `BankAccounts` in Raphael, so the idea of objects, inheritance and polymorphism is on screen. The boxes say `Savings extends Account`.
2. **The big picture.** Use "How the classes fit together" above to show the hierarchies.
3. **Interface and abstraction.** `Tracer` (section 3), then `Structure` (section 2).
4. **Inheritance and constructor chaining.** Follow `BinaryTreeStructure` up to `Structure` (section 4), then `ObjectNode` to `HeapObject` (section 5).
5. **Polymorphism.** The single `renderer.render(...)` line (section 6). This is the strongest example.
6. **Encapsulation.** `TraceModel.setStep()` (section 1).
7. **Exception handling.** Follow one error from `JdiTracer.launch()` up to the window (section 9).
8. **Observer.** Press a step key, and explain how every panel updates (section 13).

## Questions you might be asked

**What's the difference between an abstract class and an interface?**
An abstract class can have fields, constructors and finished methods, and a class can extend only one. `Structure` is abstract because its subclasses share code. An interface is only a contract, apart from `default` methods, and a class can implement many. `CallStackPanel` extends `JPanel` and implements `TraceListener` at the same time.

**What's the difference between overriding and overloading?**
Overriding is the same method with the same parameters in a subclass, and Java chooses while the program runs (`render()`). Overloading is the same name with different parameters in one class, and the compiler chooses (`Scene.add()`).

**Why does `super(...)` have to be the first line?**
The parent's part of the object has to exist before the subclass's part can use it, so objects are built from the top of the hierarchy down.

**What's the difference between checked and unchecked exceptions?**
The compiler makes you handle a checked exception, or declare it with `throws`. `TraceException` extends `Exception`, so it's checked, because a program failing to start is something callers must plan for. `IllegalArgumentException` in `setSpeed()` is unchecked, because it means a bug in the calling code.

**Why are the fields `private` if there are getters anyway?**
A getter lets others read a value but not change it. And a setter can check the new value, as `setStep()` does. A public field can't do either.

**Why not put everything in one class?**
Each class has one job, so a change stays in one place. To support a new kind of structure, I add one `Structure` subclass and one renderer, and the window doesn't change.
