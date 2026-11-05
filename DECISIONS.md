# Decisions

## Stage 0: Setup

- Why did we use WSL:

WSL (Windows Subsystem for Linux) runs a real Ubuntu system inside Windows, in a lightweight virtual machine. I develop inside it to keep my main machine clean: if the setup breaks, I can delete Ubuntu and Windows is untouched. Everything was installed with one command, `sudo apt install openjdk-21-jdk maven` (Java 21 and Maven 3.8.7). WSLg lets Linux open normal windows on my screen, which Raphael needs for its Swing window.

The project lives inside Linux at `~/raphael`, because Linux reads Windows files (`/mnt/c/...`) slowly. I enter Linux through the Ubuntu app, or by typing `wsl` in PowerShell. A prompt ending in `$` means I'm in Linux.

Raphael will still run on Windows, because Java is "write once, run anywhere". Any computer that runs it needs a full JDK, though, since Raphael compiles and debugs code.

- What does JDK give us:

The JDK (Java Development Kit) is the full Java toolbox. The JRE only runs programs; it can't compile or debug them, so Raphael needs the JDK. How a program runs:

`App.java` → **javac** → `App.class` (bytecode) → **JVM** → machine code

The `.class` file *is* the bytecode. Bytecode isn't machine code: the JVM (Java Virtual Machine) translates it while the program runs. That's why the same `.class` runs on both Windows and Linux. You get one `.class` per class, not per file: `Node` and `BST` in one file give `Node.class` and `BST.class`.

Raphael uses four parts of the JDK: **javac** to compile the student's code, the **JVM** to run it as a separate process, **JDI** (Java Debug Interface) to pause it after each line and read its variables, and **Swing** to draw the window. All four are built in, so none of them are dependencies.

- Maven and pom.xml

Maven is a build tool from Apache, like a smarter Makefile. It downloads libraries from Maven Central (an online store for Java libraries), compiles the code, runs the tests and runs the program. Libraries are saved in `~/.m2`, so each one is downloaded only once. Maven expects a standard layout: code in `src/main/java`, tests in `src/test/java`, and output in `target/`. Eclipse and other IDEs understand this layout too.

The tests are unit tests: small methods that call my code and check the answer automatically. For example, compile code missing a `;` on line 3, then check the error says "line 3". JUnit runs them with `mvn test`.

pom.xml is the recipe card Maven follows, written in XML tags like HTML (`<version>5.11.4</version>`). Every library is identified by:
- `groupId`: who made it, a web address written backwards (`org.junit` = junit.org)
- `artifactId`: its name (`junit-jupiter`; Jupiter is the nickname for JUnit 5)
- `version`: which release (`SNAPSHOT` = work in progress)
- `scope`: when it's needed (`test` = only for tests; no scope = needed by the app)

The pom also sets Java 21 and lists three plugins (tools that give Maven its jobs): **compiler** runs javac, **surefire** runs the tests, and **exec** runs `raphael.App`. The lines at the top (`xmlns`, `modelVersion`) are standard template lines and can be ignored.

- What does App.java do right now?

It prints "Hello, Raphael!", which proves Java, Maven and the project layout work together. Later it will start the app and open the window. It follows three rules: the folder matches the package (`raphael/` → `package raphael;`), the file name matches the class name (`App.java` → `public class App`), and `main` is where the program starts.

Running `mvn -q compile exec:java`: Maven reads the pom, `compile` turns `App.java` into `target/classes/raphael/App.class`, and `exec:java` runs its `main`. `-q` (quiet) hides Maven's own messages.

## Stage 1: Compiling

- How does Java code become something that runs?

`javac` reads the `.java` text and writes one `.class` file (bytecode) per class. Like `gcc`, it prints nothing when it works. `java ClassName` then starts the JVM, loads that class and calls its `main`. Errors look like `Broken.java:3: error: ';' expected`: file, line, message.

- How does Raphael call the compiler?

The compiler is a library inside the JDK, so Raphael calls it like any other object instead of running a terminal command. `ToolProvider.getSystemJavaCompiler()` returns a `JavaCompiler`. `JavaCompiler` is an interface: a list of methods with no code behind them, so `new JavaCompiler()` isn't allowed. A hidden class in the JDK implements it, and `ToolProvider` hands us one. On a JRE it returns `null`, which is why Raphael needs a full JDK.

- How do we get errors with line numbers?

The compiler reports each problem as a `Diagnostic` object with `getKind()` (ERROR or WARNING), `getLineNumber()` and `getMessage(null)`. We pass a `DiagnosticCollector` to `getTask(...)`. It gathers them, and afterwards we loop over them. So the line number is a real number in a variable, which the editor uses to mark the line.

`getTask(...).call()` returns `true` only when there are no errors. Warnings (like the deprecated `new Integer(5)`) still land in the collector but don't make the compile fail. So success is decided by that `boolean`, never by "is the list empty?", and Raphael keeps only the ERRORs.

- Why -g?

By default `javac` keeps line numbers but throws away the names of local variables; in bytecode they're just numbered slots. `-g` (like `gcc -g` for `gdb`) keeps them in a `LocalVariableTable`. `javap -l Broken.class` showed it: without `-g` there's only a `LineNumberTable`; with `-g` there's also a table listing `args`, `x` and `y`. Raphael needs the names to show `x = 5` instead of `slot 1 = 5`. Options go to `getTask` as a list of words, the same words you'd type after `javac`: `List.of("-g", "-d", dir)`.

- What do SourceCompiler and CompileResult do?

`SourceCompiler.compile(String source)` takes the code from the editor and:
1. finds the public class name with a regex, because the file must be called `ClassName.java`;
2. writes the text into a new temporary folder;
3. compiles it with `-g` and `-d` (destination: put the `.class` files in that folder);
4. keeps only the ERROR diagnostics, each as a `CompileError(line, message)`;
5. finds the class that has `public static void main(String[] args)`. It loads each compiled class without running it and checks. This matters because the class with `main` isn't always the public one, or the first one. If there's no `main`, that's reported as an error.

`CompileResult` is a record: a compact class, like a C `struct`, whose fields Java makes private and final, with the constructor and getters written for us. It holds `success`, `errors`, `classDir` (where the `.class` files are) and `mainClass` (which class to run).

## Stages 2–4: Running, reading memory, rewinding

- How does Raphael pause the program after every line?

It starts the student's program in a second JVM in debug mode, using JDI (Java Debug Interface, part of the JDK). `JdiTracer` asks JDI to tell it whenever one of the student's classes is loaded, then puts a breakpoint on the start of every line of that class. Java's own classes (`java.*`, `javax.*`, `sun.*`, `jdk.*`) get no breakpoints, so we never pause inside `println`.

JDI talks to us through events: "a class was loaded", "a breakpoint was hit", "an exception nobody caught". The program stays paused while we handle each event, and then we resume it. At every breakpoint, `HeapReader` takes a snapshot.

At first I used JDI's "step to the next line". But in a one-line loop like `while (true) { x++; }` the line number never changes, so that step never finished. A breakpoint fires on every loop iteration, so breakpoints fixed it.

- What keeps it from running forever or freezing?

Safety limits in `TraceLimits`: after 2,000 steps the program is stopped (probably an infinite loop), and if it does nothing for 5 seconds it's stopped too. Each step captures at most 300 objects and 64 cells per array.

Two `OutputDrainer` threads read the program's output (stdout and stderr). Without them, the pipe between the two JVMs fills up and the program freezes inside `println`. Text from the input box is written to the program's `System.in`, which is then closed, so `Scanner` sees the end of the input instead of waiting forever.

An exception that nothing catches becomes the last step, with its message (like "Index 5 out of bounds for length 3"). We get the message by calling `getMessage()` inside the paused program.

- How does Raphael read the memory?

`HeapReader` starts from the local variables of every running method and the static fields. It follows every arrow to the objects they point at, then their fields, and so on. That's a breadth-first search (BFS) over a graph, with a "seen" check so a cycle can't loop forever. Primitives and Strings are stored as text, `Integer` and the other wrapper types as their number, and Java's own objects (like `Scanner`) only by name.

Every object has an id from JDI (#12). An arrow is stored as that id, not as the object itself. That's how one object can be pointed at from many places.

- What's in the model?

The `model` package holds plain Raphael objects with no JDI in them, so the rest of the app doesn't depend on the debugger:
- `Value` (interface): `PrimitiveValue` ("5"), `ReferenceValue` (#12) or `NullValue`.
- `HeapObject` (abstract): `ObjectNode` (an object with fields) or `ArrayNode` (an array).
- `FrameSnapshot`: one running method, its line and its locals.
- `Snapshot`: everything at one pause.
- `Trace`: the list of snapshots, the program's output and the `EndReason` (finished, step limit, exception or timeout).

- How does going back work?

Each `Snapshot` is a complete copy of the state and is never changed after it's made. Going back one step just means showing snapshot `i - 1`. That's the Memento pattern: save the state, restore it later.

## Stage 5: Recognising shapes

- How does Raphael know what's a list or a tree?

`StructureDetector` only looks at the types and names of each class's fields, never at what the program is called or what it does:
- an array → **array**; an array of arrays (`int[][]`) → **grid**
- exactly 1 field of the class's own type (`Node next`) → **linked list**
- 2 such fields named `prev`/`next` → **doubly linked list**
- `left` and `right` (or any other 2 such fields) → **binary tree**
- anything else → a plain **box** listing its fields

So any student's `Node` class works, whatever its data fields are called. If a class doesn't match a shape, it still shows up as a box.

- What do the Structure classes look like?

`Structure` is abstract, and every kind is a subclass: `ArrayStructure`, `GridStructure`, `GenericStructure` (box), and `LinkedStructure`. `LinkedStructure` is itself abstract and has `LinkedListStructure` and `BinaryTreeStructure` under it. So there are two levels of inheritance: `Structure → LinkedStructure → BinaryTreeStructure`. `LinkedStructure` holds what lists and trees share: the nodes, following a link (`follow`), and the text inside a node (`getLabel`). Each subclass answers `getKind()` and `getObjectIds()` in its own way, and the code that uses them only sees `Structure` (polymorphism).

- What about half-finished or broken structures?

While a list is being reversed, its nodes form two chains at once (`prev: 2 → 1` and `curr: 3 → 4`). `LinkedListStructure` keeps every chain of its class together. A head is a node nobody's `next` points at. A cycle (a common bug) can't make it loop forever, because every walk keeps a "visited" set, and the arrow back to the start is kept so the bug shows in the picture. A tree has one root per tree: a node nobody points at.

- How is it tested?

`StructureDetectorTest` builds small snapshots by hand (no second JVM needed) and checks each rule: array, grid, list, a half-reversed list, doubly linked list, tree, box, and a cycle that must finish within 2 seconds.

## Stage 6: Drawing

- How does a Snapshot become a picture?

In two halves. First, `SceneBuilder` turns every snapshot into a `Scene`: shapes (`SceneNode`: a cell, a list node, a tree circle, an object box, a variable label) with exact positions, plus the arrows between them (`SceneEdge`). Then `ScenePainter` draws a scene with Java 2D, where (0, 0) is the top-left corner and y grows downwards. All the scenes are built once, right after tracing, so stepping only has to draw.

Every shape has a key that stays the same from step to step: `#12` is object 12, `#7[2]` is cell 2 of array 7, `var:curr` is the label of `curr`. That's what lets the animator slide the same shape to its new place.

- Who decides where things go?

There's one renderer per kind of structure, each a subclass of the abstract `StructureRenderer`:
- `ArrayRenderer`: a row of cells with the index underneath
- `GridRenderer`: a table with row and column numbers
- `LinkedListRenderer`: `[ value | • ]→` boxes in a row, with a slash for a null `next`
- `BinaryTreeRenderer`: x from the in-order position, y from the depth
- `BoxRenderer`: the class name on top, then one line per field

`StructureRenderer.forStructure()` picks the right one (the Factory pattern), so `SceneBuilder` only ever calls `render()` (polymorphism). `SceneBuilder` stacks the structures from top to bottom, then adds the variable labels above the objects they point at. In recursion, the newest `n` wins.

- How do the reversal arrows "flip"?

`ListOrder` remembers a left-to-right place for each list node for the whole run. A node gets its place the first time it's linked into a chain (right after the node before it) and keeps it. So while a list is reversed, the nodes stay put and only the arrows change direction. An arrow pointing backwards is drawn as a curve underneath, so it doesn't cross the nodes in between.

- Why does a BST look sorted?

Each node gets its own column in in-order (left subtree, node, right subtree), which for a BST is sorted order. Its row is its depth.

- How was it checked?

`SceneBuilderTest` checks three things: reversing a list keeps every node where it was, a tree's nodes come out in in-order and by depth, and a label sits right above its object. I also rendered steps of every example to PNG images and checked them by eye. That's how I found that the grid label covered a column number, and that arrowheads were hidden in doubly linked lists.

## Stage 7: The window

- How is the window organised?

`MainWindow` (a `JFrame`) is made of panels that each do one job:
- `CodePanel`: the editor
- `VisualPanel`: the picture
- `ControlsBar`: the step buttons, slider and speed
- `CallStackPanel`: the methods running right now
- `LocalsPanel`: the variables
- `ConsolePanel`: the output, plus the input box for `Scanner`

The toolbar has Open, Save, Examples and Run. The six examples are `.java` files inside the app's resources (`src/main/resources/examples/`). FlatLaf gives Swing its modern look and is set up in `App.main`.

- How do the panels stay in sync?

With the Observer pattern. `TraceModel` holds the loaded run and the current step. Every panel implements `TraceListener` and registers with the model. Whatever changes the step (a button, the slider, Play) calls `model.setStep(...)`, the model tells every panel, and each panel redraws itself. No panel knows about any other panel. `setStep` keeps the step inside the run, so a panel can never be asked to show step −1 (encapsulation).

- Why doesn't the window freeze?

Swing has one thread that draws the window and handles clicks (the event dispatch thread). If tracing ran on it, the window would hang for the whole run. So Run starts a `SwingWorker`. Its `doInBackground()` compiles, traces and builds the scenes on a worker thread, and its `done()` hands the result back on the Swing thread. Status messages ("Compiling…") are sent with `publish()`. An error thrown in the background (like a `TraceException`) comes back wrapped in an `ExecutionException` and is shown in the status line.

- What happens with compile errors and crashes?

Compile errors are marked red in the editor (hover to see the message) and listed in the console, and the step controls stay off. A crash makes its line red, puts "Crashed here: …" at the top of the picture, and shows Java's stack trace in the console. Editing the code clears the old run, because its steps no longer match the code.

## Stage 8: Animation

- How does a step animate?

`Animator` uses a Swing `Timer` that ticks about 60 times a second. On each tick it builds an in-between scene with `position = start + (end − start) × progress`, where progress goes from 0 to 1 over about 0.4 seconds. The same formula on transparency makes new shapes fade in and removed ones fade out. Progress is eased (slow, fast, slow), so the movement looks natural.

- How does it know what changed?

Every shape has a stable key, so `Diff.between(old, new)` can compare the two scenes key by key:
- only in the new scene → added
- only in the old scene → removed
- a different position → moved
- a different value → changed

Changed values flash yellow and keep a soft glow until the next step, so in BubbleSort you can see which cells were just swapped. Stepping back works the same way, just from a later scene to an earlier one.

- What if you click again in the middle of an animation?

The next animation starts from wherever the shapes are on screen right now, so nothing jumps. While playing fast, each animation is shortened so it ends before the next step.

- How was it checked?

`AnimatorTest` checks that `Diff` sorts shapes into added, removed, moved and changed correctly. Halfway through an animation, a moving shape is exactly halfway and new or removed shapes are half transparent. At the end, the picture equals the new scene with the changed value glowing. `TraceModelTest` checks that the step stays inside the run and that listeners are told about each real change exactly once. I also rendered in-between frames of a BST insert, and screenshots of the whole window, to check by eye.

## Stages 9–10: Style and hand-in

- Where does the look come from?

Every colour, size, font and timing lives in `render/Style.java`, so restyling Raphael means changing that one file. The window uses FlatLaf's light theme.

- What was handed in?

`README.md` explains how Raphael works, which file does what (with its key methods), and where each OOP concept is used. Each class also has an `// OOP:` comment at the top. `DEMO.md` is a five-minute demo script. `mvn test` runs 27 tests across compiling, tracing, detection, layout, animation and the model.

## Polish: smoother motion, index markers and the look

- Why didn't the animation feel smooth enough, and what changed?

Five things were wrong:
1. Every step restarted a fixed 0.38 s slide that started from standing still, so fast stepping stopped and started.
2. The panel redid its layout on every frame.
3. Linux didn't push each frame to the screen straight away.
4. Text jumped to whole pixels while boxes moved smoothly, so values wobbled. I measured it: `drawString` at x = 10.25 gave exactly the same pixels as x = 10.0.
5. A changed arrow faded out while the new one faded in.

Now every shape is pulled to its new place by a spring (`Spring`): `x(t) = target + (a + b·t)·e^(−ω·t)`. It's "critically damped": just enough friction that it never overshoots. When a new step changes the target, the shape keeps its current speed, so the motion flows. The panel only redoes its layout when the picture's size changes, `Toolkit.sync()` pushes each frame out, and text is drawn as glyph outlines at exact fractional positions. Painting one frame takes under 1 ms on average, against the 16.7 ms a frame gets at 60 fps.

- How does an arrow "swing"?

Every arrow is a Bézier curve: a start, two control points and an end. A straight arrow is a curve whose control points sit on the line, so a straight arrow and the curved "pointing back" arrow can blend into each other. An arrow keeps its key when its target changes ("#12.next→" is node 12's next pointer, whatever it points at), so the animator knows it's the same arrow. Sliding the tip straight across would pass right through the start, and the arrow would vanish halfway. So the tip turns around the start like a clock hand instead, going underneath when it flips from right to left. A pointer set to `null` pulls back into its slot, and a new one grows out of it.

- How do the i/j markers know which variables are indexes?

`IndexUsage` reads the program's text. `arr[j + 1]` means `j` is an index into `arr`, `paths[r][c]` means `r` indexes rows and `c` columns, and `i < arr.length` means `i` walks along `arr`. A variable that is only ever used with an offset (like `rows` in `paths[rows - 1]`) is a size, not a position, so it doesn't get a marker. At each step, the marker sits under the cell the variable's current value points at, so it slides along as the loop runs.

The same reading finds which cells the next line touches: accesses before the `=` are writes (solid outline) and the rest are reads (dashed). If an index is past the end, a red ghost cell appears where the program is about to crash. When a line simply copies one cell into another (`arr[j] = arr[j + 1]`), the value flies across.

- What do the colours mean?

Blue is "about to happen": the next line, variable labels, markers and outlines. Amber is "just changed": changed values, changed variables, new output. Grey is the line that just ran, because the picture shows the state after it. Red is an error. Everything else is black and white in one monospace font, with square thin lines, to keep the plain terminal feel.

- What else was added?

- Step over and step out, which skip the inside of a call by watching how deep the call stack is.
- Clicking a line number jumps to the next time that line runs.
- Keyboard shortcuts: arrows, Space, Home/End, Ctrl+Enter.
- Ctrl + mouse wheel zooms, for a projector.
- Widths that stay the same for the whole run, so an array doesn't jump when 9 becomes 10.
- A seventh example, OffByOne, for the ghost cell.

## B-trees

- How does Raphael draw a B-tree?

A B-tree node keeps its children in an array (`Node[] children`), not in fields like `left` and `right`, so at first Raphael drew each node as a plain box with two separate arrays, and the tree shape was lost. Now `StructureDetector` has one more rule: a class with an array of its own type is a multi-way tree (`MultiwayTreeStructure`, another subclass of `LinkedStructure`). Its keys are the class's other array field, and its count is an int field called `n`, `size` or `count`. The node's arrays belong to the tree, so they aren't drawn again on their own.

`MultiwayTreeRenderer` draws it like a textbook. Each node is a row of key cells, faded past `n`, with a row of small child-pointer slots underneath. Leaves sit side by side, and each parent is centred over its children. If a parent is wider than its children, its whole subtree moves right so nothing overlaps.

- Why do the markers and flying keys work on it?

The key cells use their array's keys (`#7[2]`), just like a plain array, so everything that already worked on arrays works on B-tree nodes too. `IndexUsage` also learned two things a B-tree needs. It reads fields through a variable (`x.keys[i]` is the keys array of the object `x`). And it reads indexes made of two variables (`y.keys[j + T]`). So during a split you see `i` and `j` move, the right half's keys fly into the new node, and the middle key flies up into the parent.

## Refining: fixes, the look, ArrayList and the font

- What was broken?

The worst bug was that the arrow keys, Home and End never stepped the program, even after clicking the picture, and only Alt + arrow worked. Swing offers a key press to the focused component, then to the components around it, and only then to the window's shortcuts. A `JScrollPane` and a `JSplitPane` both take those keys for themselves, so our shortcuts never heard them. `MainWindow` now removes those bindings from the three panes around the picture. I only found this by sending real key events to the window, and `MainWindowTest` now checks for it.

Focus also stayed in the editor after a run, so `requestFocusInWindow()` now moves it to the picture. The editor had no undo, and picking an example wiped your code without asking. `UndoManager`, which comes with the JDK, records each edit, and a `dirty` flag remembers whether the text changed since it was loaded or saved. The line being run could be cut off, so the divider is now placed from the width of the longest line, between 40% and 55% of the space. The window was always 1300 × 840, so it now takes the smaller of that and the usable screen.

The dots in pointer slots never showed. Java 2D paints in order, and the arrows were painted before the boxes, so each box covered its own dot. `ScenePainter` now makes a second pass after the boxes, for just the dot and the part of the arrow inside the slot.

- How does the picture fit the window?

`TraceModel.getRunSize()` finds the room the biggest picture of the whole run needs. `VisualPanel.fitToRun()` turns that into one scale, as big as fits, between 0.6 and 1.5, and centres the picture. It's one scale and one offset for the whole run, so nothing jumps between steps, for the same reason as the fixed widths in `RenderContext`. Text is drawn as outlines, so it stays sharp when scaled. Ctrl + wheel still zooms on top of this.

- What else changed in the look?

Box outlines are grey instead of near-black, so blue and amber stand out more. A cell being written gets a blue tint and one being read a paler one, painted see-through so the value and any amber still show. The outlines moved just inside their cells, so neighbours don't collide. Run is the only filled button. The slider takes the spare width, because `BorderLayout` gives it to the centre. The bottom panels sit flush with thin lines between them, and variables get the most room. The stdin box only appears when the code contains `System.in`.

- Why did the variables text change?

`Node#56{val=1, next→#58}` showed the debugger's ids, which mean nothing to a student and match nothing in the picture. Now it's `Node{val=1, next→2}`. `describe()` became `describe(Snapshot)`, because it has to look up what a reference points at, and `shortName()` is the short form of that target. Both are overridden in `ObjectNode` and `ArrayNode`, so this is still polymorphism. A grid shows only its size, `int[4][]`, because its cells are in the picture. One test checked the old text, so I changed it to expect the new text.

- How does ArrayList work?

An `ArrayList` is really an array plus a size. It keeps its items in a private array called `elementData`, and only the first `size` cells are in use. The debugger can read private fields, so `HeapReader.readArrayList()` reads those two and builds a normal `ArrayNode`. `StructureDetector` and `ArrayRenderer` needed no changes. `HashMap`, `LinkedList` and `Stack` store things differently, so they're still shown by name only. `list.get(i)` doesn't get an `i` marker, because `IndexUsage` only looks for square brackets.

- Why bundle a font?

The window used whichever monospace font the computer had, which here was DejaVu Sans Mono. Now JetBrains Mono ships inside the app, so it looks the same everywhere. `Style.pickMono()` registers the two font files with Java, and if that fails it falls back to an installed font. It's a resource file under an open licence (`OFL.txt` is beside it), not a code library. I used the "NL" (No Ligatures) version, so `->` and `!=` can never be drawn as joined symbols, which would be wrong in a tool that teaches code.

- How was it checked?

`mvn test` runs 61 tests. I also drove the real window from a throwaway program: real key events, undo, the prompt, the stdin box, a compile error, an infinite loop and zoom. I checked screenshots of the examples, an `ArrayList` program and a small 1024 × 700 window by eye.

## Making it work on anyone's code

- Why couldn't I run my own code?

Partly it did run. The first step of every program comes before any line has run, so the picture said "no arrays or objects at this step" and nothing moved, which looked like a failure. Now pressing run plays the program by itself. Any step made by hand pauses it, because `ControlsBar` notices a step change that its own timer didn't cause.

Partly it really didn't. I tested about 25 programs of the kind a teacher might paste, and valid Java was being rejected: a `package` line, `public final class`, and the word "class" in a comment.

- How does Raphael run any pasted program now?

javac has two rules about files: a public class must match its file's name, and a package must match its folder. Pasted code has no file, so neither rule means anything. So `SourceCompiler` finds the top-level text by replacing comments, strings and everything inside `{ }` with spaces, then blanks `public` and `package` there. They're replaced by the same number of spaces, so every error still points at the right line, and the editor's text isn't touched. It also cleans invisible characters that web pages add, like non-breaking spaces. A `main` that isn't `public static` is still an error, because Java itself won't run it.

- How does it show Stack, HashMap and the others?

Every Java collection can list its items with `toArray()`, however it stores them. Raphael already called `getMessage()` inside the paused program for exceptions, so `HeapReader` calls `toArray()` the same way and stores the result as a normal `ArrayNode`. This replaced the earlier code that read `ArrayList`'s private fields, so there's one way to do it for all of them. A map lists its entries as `key=value`.

- How do the colours work?

Swing draws each line of a text area with one method, `PlainView.drawUnselectedText`. `CodePanel.ColoredView` overrides it to draw each piece of the line in its own colour, which is a good example of inheritance and overriding. `JavaTokens` finds the pieces with one regular expression. Comments and strings come first in it, so words inside them aren't treated as code. I wrote this instead of adding a library because it's about 100 lines I can explain. The colours avoid blue, amber and red, which already mean something in Raphael.

- What did the design critic find?

I had a separate agent review real animation frames as a first-time viewer. Its role is in `.claude/agents/raphael-design-critic.md`, and it only reports. The main findings:
- Correct code could show a red crash warning, because Raphael read the line's text and didn't know `&&` protects the array access. The run is already recorded, so now red only appears when a crash really follows.
- `this` pointed at blank space, because an object exists before its fields are set. Now it's a faded `new` cell.
- `i` at -1 was faint and cut off by the window's edge. -1 is often why a loop stops, so now it's full strength next to a faded ghost cell.
- New nodes flew through other structures. Now arrays stay on top, and a node that isn't linked yet waits beside its tree.
- Read and write boxes flew between unrelated arrays. Their keys now include the array's id, and a new key means a new shape, so they fade instead of travelling.
- A marker like `j` showed on every node's `keys`. Now it only shows on `x.keys` while the code around that step uses `x.keys`.

- How do real programs stay big enough to read, and quick enough to run?

Every array used to take a whole row, so a program with a few collections shrank to the smallest scale. Now small arrays share a row, and each keeps room for its longest length from the whole run, so a growing list doesn't push its neighbour. Room for the arrays on top is kept for the whole run too, so a new array doesn't push a tree down. It's the same idea as the fixed widths.

Calling into the paused program is slow, and a program with busy collections took 54 seconds to record. A boxed number like an `Integer` can never change, so `HeapReader` now reads each one only once, and it reads a map entry's fields directly instead of calling its methods. That took the same run down to about 23 seconds, and most programs are far lighter.

- How does it run from Windows?

Three ways, and they build on each other. Open and save start at the Windows Desktop, because WSL can see Windows's files under `/mnt/c`. A double-click file, `Run Raphael.bat`, starts the app through WSL. And `scripts/build-windows.sh` makes a real `Raphael.exe` with `jpackage`, which comes with the JDK. Raphael needs Java's compiler and debugger while it runs, so the `.exe` carries its own private copy of Java, and nothing has to be installed.

- How was it checked?

`mvn test` runs 89 tests at this point. A throwaway program drove the real window through the whole demo: press new, paste a program with Windows line endings through the real clipboard, press Ctrl+Enter, watch it play, and check the output. The test programs and the critic's frames are not part of the project.

## Final polish: dark mode, text size and the last checks

- How does dark mode work?

Every colour already lived in `Style`, and everything reads those colours when it paints. So `Style.useTheme(dark)` assigns all of them at once from a light and a dark value, and the next repaint uses the new ones. The colours stopped being `final` for this. `MainWindow.toggleTheme()` then tells FlatLaf to switch, and `applyTheme()` walks through the window's components for the few colours that are set once rather than read at paint time. The meanings stay the same: blue is next, amber is changed, red is an error. A test measures the contrast of the dark colours with the standard formula, so text stays readable.

Swinging the look replaced two things by surprise. `updateUI` would have put back the plain text drawing, so the editor overrides it to keep the colours. And it gave the editor a new highlighter, so `CodePanel.restoreMarks()` puts the line marks back.

- How does the text size work?

`Style.setCodeSize()` changes the code font within limits, and `MainWindow.applyTextSize()` gives it to the editor, the variables, the call stack and the console. Dark mode and the text size are remembered with `Preferences`, a JDK class for small saved settings. Ctrl + and Ctrl - used to zoom the picture, but the picture already scales to fit, and text was what a projector needed. Ctrl + wheel still zooms the picture.

- What are the editor helpers?

`EditorKeys` adds what people expect from a code editor: Enter keeps the indentation, Tab and Shift+Tab move lines, `}` lines up with its block, Ctrl+/ comments lines, and Ctrl+F finds text. It's a separate class that holds the text area rather than extending it, which is composition. Each change to several lines is one edit, so one undo reverses it.

- What did more testing find?

Another 21 teacher-style programs showed one real hang: recursion with no base case. At every step Raphael read the whole call stack, which kept growing, so the work grew with the square of the depth. Now it reads only the newest 32 calls. A run that's still too long can be stopped: the run button becomes **stop**, a flag tells `JdiTracer` to end the program, and you keep the steps so far. The status bar counts the steps while recording. The waiting loop now looks up every 100 ms, so stop works even when the program is idle.

- Why does the launcher run dist/ now?

While testing, the app crashed at start with "Unresolved compilation problems". VS Code's Java extension compiles into the same `target/classes` folder as Maven, and it had left a broken `App.class` there. Maven saw nothing to recompile, and the tests passed because they never call that method. So `scripts/build-app.sh` compiles with `javac` into a private folder and packages the result into `dist/`, and the launcher runs that. Nothing else writes there, and it needs no Maven and no internet.

- What did the critic find this time?

- An arrow from an array cell ran under the next cell, so it looked like that cell's arrow. Now a slot's arrow leaves through its own bottom edge, and an arrow to a whole array lands on its first cell. An arrow up to an array goes round the left side, so it doesn't cross another array. Objects stay on one row for longer, and an array's arrow that still has to cross a box shows faintly on top of it, so it can't look like it stops there.
- A program waiting for input looked like a crash. Compile errors looked like nothing had happened. Now one red banner in `VisualPanel` covers crashes, missing input and compile errors, wrapped so nothing is cut off, and Java's own lines are left out of a stack trace.
- `balance += amt` also lit up `id`, because a box was one shape holding all its fields. `BoxRenderer` now makes one shape per field, each with its own key, so only the changed one flashes.
- The class header seemed to run. For a generic method like `compareTo`, javac adds a hidden bridge method with the header's line number. `JdiTracer` now skips bridge methods.
- Inheritance wasn't visible. A box's title now says `Savings extends Account`, read from the class through JDI, and an array that only a field points at is labelled with that field's name.
- Dragging the slider lit everything amber. Amber means one step changed this, so `Animator.jumpTo()` glides without flashing.

- What was the bug that recorded 0 steps?

About 1 run in 60 finished with the right output but no steps, so Raphael looked like it had done nothing. The second JVM starts paused, and JDI counts pauses and releases. `JdiTracer` released it twice: once with `vm.resume()`, and once when handling the "VM started" event. If "a class was loaded" paused the JVM between those two, the extra release cancelled that pause, and the program ran on before its breakpoints were set. Now it's released once. It had been there since Stage 2, and only showed up through running the same programs many times.

## Shipping

- Why add BankAccounts?

The course is about OOP, but no example in the menu used it. BankAccounts has an interface, an abstract class, two subclasses, an overridden method and a caught exception, so inheritance and polymorphism show up in the running picture. `ExamplesTest` now runs every program in the menu, so a change can't quietly break one.

- What's in the repository?

The source, tests, docs, examples and scripts. The build output (`target/`, `dist/`) isn't, because anyone can rebuild it with `scripts/build-app.sh`. `.gitattributes` keeps `.bat` files with Windows line endings and `.sh` files with Linux ones.

## Version 2: from course project to product

- Why keep going after the submission?

My teacher liked Raphael and asked for more: more Java (exception handling, file handling, catching errors), testing the edge cases, and a detailed report. I decided to go further and make it a real product that anyone can download. It should have no bugs, work on any machine and not just my laptop, and do better than the tools that already exist. [ROADMAP.md](ROADMAP.md) lists the stages.

- Who else does this?

I looked at the tools a student or teacher would use instead:
- **JavaWiz** is the closest. It comes from JKU Linz, has won awards and was presented at a conference in 2025. It records the run so you can step back, and it has ten synchronised views: a memory diagram, a trace table, a flowchart, a sequence diagram and more. But it runs inside VS Code or IntelliJ and needs JDK 25 or newer installed.
- **Python Tutor** is a website that covers five languages. It stops at 1,000 steps and draws every structure as plain boxes and arrows.
- **jGRASP** recognises lists, trees and hash tables, but it's an older IDE whose viewers you open by hand.
- **VisuAlgo** has polished animations, but only of its own built-in algorithms.

Raphael's place is: paste any Java program, with no setup, and get a textbook-quality animated picture, plus a plain explanation when something goes wrong. B-trees, grids, `i`/`j` markers and spring animation are things none of them do. JavaWiz's views are worth having too, so stage 9 adds them, with each one linked to the others.

- Which parts are ours?

All the logic is ours: the spring animation, the layouts, recognising the structures and reading the code for indexes. The JDK gives the building blocks: the compiler, the debugger interface, Java 2D for drawing a line or a letter, and Swing for the window. FlatLaf, the only outside library, styles the buttons and menus. No animation library, layout library or online service is used.

- Why a desktop app, and why only Java?

A desktop app keeps everything built so far, works offline and costs nothing to run. A website would mean rewriting the window and drawing in JavaScript, and running strangers' code on a server. IDE plugins come later. IntelliJ is built with Swing too, so Raphael's own panels can sit inside it.

Other languages are possible later, because only three files depend on Java's tools (`SourceCompiler`, `JdiTracer`, `HeapReader`). Each language needs its own tracer, though. C/C++ would help my university most, but it's the hardest. So this version stays Java only.

- Why open source?

Teachers and universities trust a tool whose code they can read, and others can help improve it. JavaWiz is open source too. The licence gets chosen at the first public release.

- What changed in how we work?

The limits from the course are relaxed: new classes and new libraries are allowed, as long as each one is explained first. The report is written alongside the work in `docs/report/`, one chapter per file, so each stage adds its own chapter.

## Stage 1: Works on any machine

- How do I test on systems I don't own?

GitHub Actions. On every push, GitHub starts a fresh Windows, macOS and Linux machine, installs JDK 21, and runs `mvn test` on each (`.github/workflows/ci.yml`). Linux machines there have no screen, and some tests open Swing windows, so they run inside `xvfb-run`, a virtual screen. All 116 tests pass on all three, and none are skipped. The badge at the top of the README shows the latest result.

- What would have gone wrong on other computers?

Five things:
- **Mac shortcuts.** Swing's `"control S"` is the Control key, but Mac users press Cmd. `Shortcuts.key()` turns "control" into "meta" (Swing's name for Cmd) on a Mac, and `Shortcuts.label()` turns "Ctrl" into "Cmd" and "Alt" into "Option" in tooltips, messages and the keys screen. The keys screen re-pads its columns, because "Option+Down" is longer than "Alt+Down".
- **The start folder.** Open and save started at `/mnt/c/Users/…/Desktop`, which only exists inside WSL. Now they start at the Desktop on any system, and at the Windows one only when `WSL_DISTRO_NAME` says we're inside WSL.
- **The Turkish i.** `toLowerCase()` follows the computer's language. In Turkish, `"SIZE".toLowerCase()` is `"sıze"`, with a dotless ı, so a B-tree's count field wouldn't be found. Every case change now uses `Locale.ROOT`, which is the same everywhere. A test runs the detector with Turkish as the language.
- **Encoding and language of the compiler.** javac read the file in the computer's default encoding and wrote its errors in the computer's language. A Japanese computer would show Japanese errors in an English app. Now it reads UTF-8 and reports in English.
- **Dark mode.** The first launch was always light. Java can't ask the system about dark mode, so `SystemTheme` runs the small command each system has: `reg query` on Windows (and through `reg.exe` inside WSL), `defaults read` on a Mac, and `gsettings` on Linux. It gives up after 2 seconds. Once you press Ctrl+D, your choice wins from then on.

- What's left for later?

Checking how it looks on a high-resolution screen needs a real screen, which CI machines don't have. A real launcher instead of `Run Raphael.bat` comes from `jpackage` in Stage 5. Both happen there.

## Stage 2: Bulletproof tracing

- How much memory can the student's program use?

256 MB, set with `-Xmx256m` when its JVM starts (`TraceLimits.MAX_MEMORY_MB`). Without a cap, a program that allocates in a loop could use up a quarter of the computer's memory, which is Java's default. With it, the program ends with an `OutOfMemoryError`, shown like any other crash. The edge-case `OutOfMemory` checks this.

- How do we make sure the program never keeps running after Raphael?

In two layers, because Raphael can end in two ways.

When Raphael quits normally, Java runs its *shutdown hooks* on the way out: threads registered with `Runtime.addShutdownHook`. `JdiTracer` registers one, once, that ends every child process Raphael started. At first I registered a hook for each run, but a test caught a gap: if Raphael quit while the program was still starting up, the hook wasn't there yet, and the program stayed frozen forever, waiting for a debugger that was gone. Ending *every* child closes that gap.

When Raphael is killed outright (Task Manager's "End task", or a crash), none of its code runs, hooks included. So the program carries its own guard. `ParentWatch` is a *Java agent*: a class that Java runs before `main`, given with `-javaagent:file.jar`. Its `premain()` asks `ProcessHandle` to tell it when its parent process (Raphael) ends, and then halts the program. An agent has to be in a jar whose manifest names it, so `ParentWatch.jar()` writes that small jar into the temp folder with a `JarOutputStream`. Raphael's own classes are excluded from tracing (`raphael.*`), so the agent never shows up as steps. To check the agent really matters, I switched it off: the "killed" test failed. Switched back on, it passes.

One gap is left. In the half second while the program's JVM is starting, it's paused before the agent has run. If Raphael is killed at exactly that moment, the program stays waiting. That's rare, and it takes no CPU.

- What did the edge-case programs find?

`EdgeCaseTest` runs the 32 programs in `src/test/resources/edge/`. Each says at its top what must happen, in comments like `// expect: EXCEPTION` and `// output: total 200`, and every step is also laid out and drawn. Raphael already handled every case: threads, `System.exit`, unicode names, cycles, nulls, a 10,000-cell array, `Integer` caching, running out of input or memory, a 3,000-character line, Windows line endings, a `package` line, a long sleep.

One message was wrong, though. A recursion with no base case reaches the 2,000-step limit long before Java's `StackOverflowError`, and the message said "If it isn't an infinite loop, try a smaller input", which sends a student the wrong way. Now, if the whole call stack is one method calling itself, it says "Stopped after 2000 steps, with down() calling itself again and again. Is its base case missing?"

- What is the soak test?

The 1-in-60 timing bug from version 1 only showed up by running the same programs again and again. `SoakTest` does that for good: every example 100 times, and every run must take exactly the same lines and print the same output. That takes about 10 minutes per system, too slow for every push. So it's tagged `soak`, a normal `mvn test` leaves it out, and `.github/workflows/soak.yml` runs it every night on all three systems.

- What is coverage?

JaCoCo, a Maven plugin, watches the tests run and records which lines and branches of Raphael they used. It writes a report to `target/site/jacoco/index.html`. Overall it's 78.5% of lines and 70.5% of branches. The engine is at 89–99% (compile, detect, model, render, trace). The window is at 58%, because most of it reacts to clicks.

## Stage 3: Raphael catches its own errors

- What's the difference between a problem and a bug?

A *problem* is something Raphael expects and can explain: there's no Java compiler (a JRE instead of a JDK), the temp folder can't be written, or the program's JVM won't start. A *bug* is something Raphael didn't expect, like a `NullPointerException` in a panel. The two are handled differently, so the code needs to tell them apart.

Every problem is now a subclass of one abstract, checked exception:

```
Exception
 └ RaphaelException (abstract)
    ├ CompileSetupException   can't get ready to compile
    └ TraceException          can't run or record the program
```

It's abstract so that only the specific kinds are ever thrown, and checked so the compiler makes every caller handle it. Each one keeps the low-level error that caused it (`super(message, cause)`), so the log still shows the real `IOException`. When a run fails, `MainWindow.runFailed()` checks `cause instanceof RaphaelException`. A problem gets a plain message in the picture. A bug goes to the safety net.

- What's the safety net?

`ErrorReporter.install()` makes it Java's *default uncaught exception handler*: the code Java calls when an exception reaches the top of any thread without being caught, Swing's thread included. Before, such an exception printed to a console nobody looks at, and a button would just stop working. Now it's written to the log, and a dialog says what happened, that the code is safe, and where the details are. It has a "copy details" button for a bug report. If the same bug happens 50 times a second (say, while painting), only one dialog shows every 10 seconds, and the rest go to the log.

- Where's the log, and what's in it?

It uses `java.util.logging`, which is part of the JDK. Each class has a `Logger` named after itself, and `AppLog.setup()` sends everything under `raphael` to a file in the usual place for each system (`AppFolders`): `%LOCALAPPDATA%\Raphael\logs` on Windows, `~/Library/Logs/Raphael` on a Mac, and `~/.local/state/raphael/logs` on Linux. When the file reaches 1 MB it rotates, and three files are kept, so it never grows without end. `LineFormatter` extends Java's `Formatter` to write one line per message. The log has the start, each run (steps and how it ended), each problem with its stack trace, and the quit. It never leaves the computer.

I also went through every `catch` block. Most are normal flow, like a line that no longer exists after an edit. But some hid real failures with no trace: the font not loading, the agent jar not being written, an example missing, a collection that couldn't be listed. Those are now logged.

- How does autosave work?

Every 5 seconds, if the code has unsaved changes, it's copied to `draft.java` in Raphael's data folder (`DraftStore`). The writing happens on a background thread, so a slow disk never pauses the window. A normal quit deletes the draft. So if Raphael finds a draft when it starts, the last session didn't end normally, through a crash, a power cut or "End task", and it offers the code back. If there are no unsaved changes, there's no draft.

- How are files saved safely?

`SafeFiles.write()` writes to a temporary file in the same folder, then *moves* it over the real file in one step. If the disk fills up or the power goes halfway, the old file is still whole, never half-written. Opening a file now refuses anything over 1 MB or with zero bytes in it (a picture renamed `.java`), which would have frozen the editor. File errors get plain reasons: `NoSuchFileException` says "it doesn't exist (was it moved or deleted?)" and `AccessDeniedException` says "you don't have permission to use it". A failed save keeps the code in the editor and asks where to save next time.

- What can be changed in settings?

The step limit (100 to 20,000), how long a program may sit idle (1 to 60 seconds), its memory (64 MB to 2 GB), the text size and dark mode. `RunLimits` is a record whose compact constructor refuses values outside those ranges, so a bad setting can't exist. If the saved settings were changed by hand and are out of range, Raphael falls back to the defaults and logs it. The window only knows the `Tracer` interface, so `setLimits()` became one of its default methods, like `stop()`. Each run keeps one set of limits from start to end, even if settings change while it records.

## Stage 4: Fast

- Where did the time go?

I measured before changing anything. Building a 500-node list (2,000 steps) took 20.6 seconds to record, and less than a second to lay out. So the recording was the problem. At every step, `HeapReader` read every reachable object again: up to 286 per step, each one a round trip between the two JVMs. That's 285,857 reads for a run where each step changes one or two objects.

- How does Raphael avoid reading everything again?

It remembers. Each object of the student's classes is kept as it was last read, and only read again once one of its fields has been written. JDI can report every write to a field (a *modification watchpoint*). `JdiTracer` asks for one on every field of the student's classes when they load, with `SUSPEND_NONE`, so the program doesn't stop for them. The reports arrive in order, before the breakpoint on the next line, and `HeapReader.fieldWritten()` marks those objects. The same run now reads 856 objects and takes 1.9 seconds, eleven times faster.

Two cases can't be trusted, so they're read every time as before. An array cell's write can't be watched, so arrays and Java's collections are always read again. And in a program with several threads, another thread can be paused just before a write, so remembering stops as soon as a second thread runs the student's code.

To be sure nothing changed, `RememberingTest` records 38 programs twice, once remembering and once reading everything, and every step must match. It found nothing wrong with remembering. It did find that a lambda's hidden class name (`Main$$Lambda/0x0000…`) was shown as is. `Names.simple()` now says `lambda`.

- What else was slow?

Every run waited about a third of a second at the end. The program's JVM sends "I'm finished" (`VMDeath`), and then, while a debugger is still attached, waits before it exits. Now `JdiTracer` lets go (`vm.dispose()`) as soon as `VMDeath` arrives, and the JVM exits about 10 ms later. An empty program went from 380 ms to 66 ms, and BubbleSort from 434 ms to 114 ms. The program's output isn't lost, because its JVM still exits normally, and Raphael reads until the pipe closes.

`ArrayList`, `Vector` and `Stack` keep their items in an array field called `elementData`. Reading that field directly is quicker than calling `toArray()` inside the paused program. Calling a method there is the slowest thing JDI does. A map entry's key and value now come in one round trip instead of two.

- Why build the pictures only when they're shown?

Memory. The pictures for all 2,000 steps took 155 MB, while the recording itself takes only 17 MB, since unchanged objects are now shared. Settings allow 20,000 steps, which would have needed about 1.5 GB. Now `SceneBuilder.build()` learns what the pictures need from the whole run (widths, markers and where each list node goes) and returns a `SceneList`. It extends `AbstractList`, so to the rest of Raphael it's an ordinary `List<Scene>`, but `get(step)` builds a picture when it's asked for, and only the 64 most recently shown are kept (a `LinkedHashMap` in access order, as a least-recently-used cache). Building one takes about a quarter of a millisecond.

The catch was list positions. `ListOrder` learned them while building the steps in order, and a node not yet linked into a list waited at the end. Built out of order, it would have jumped straight to its final place. So `ListOrder` now also remembers the step at which each node got its place, and only counts it as placed from that step on. `SceneListTest` builds runs twice, reads one forwards and one backwards (which also forces rebuilds after the cache lets steps go), and every step must be the same.

- What did it come to?

| | Before | After |
|---|---|---|
| Recording a 500-node list (2,000 steps) | 20.6 s | 1.9 s |
| Objects read for it | 285,857 | 856 |
| An empty program | 380 ms | 66 ms |
| BubbleSort | 434 ms | 114 ms |
| Memory for the pictures of 2,000 steps | 155 MB | under 1 MB |
| Drawing a frame of the biggest picture | — | 2 ms on average, 7 ms at worst (60 fps needs under 16.7) |
| Opening Raphael's window | — | about 0.5 s |

A timing test would be flaky on CI's shared machines, so the test counts work instead: the 500-node list must read fewer than two objects per step.

## The first UI pass (after Stage 4)

- Why a design agent?

The interface still looked dated next to today's apps: Swing's own file chooser beside Windows' Explorer dialog was the clearest example. I added a "chief UI designer" agent (`raphael-ui-designer`, running on Sonnet, which costs less). It builds Raphael, takes screenshots of the real window and every dialog in both themes and at 200% scaling, and writes exact specs. It doesn't edit code. Claude builds its specs, so the code stays in one style I can explain. From now on, every stage ends with a pass like this.

- What did it find?

Nine things. They're ranked, with mockups in `docs/design/`, and the ones not built yet are listed in ROADMAP stage 4b. The ones built now:
- **The system's own file dialogs.** `java.awt.FileDialog` opens Explorer's dialog on Windows, Finder's on a Mac and GTK's on Linux, with the sidebar, search and recent folders people know. I checked that Java picks the GTK one here, inside WSL. Windows ignores a file-name filter, so the dialog is given `*.java` there instead. Save As (Ctrl+Shift+S) suggests the public class's name, because Java expects the file to be named after it. Raphael remembers the last folder. Open now asks about unsaved code *before* the dialog, not after you've picked a file.
- **Contrast.** Hint text was 3:1 on white, and white text on the dark theme's blue run button was 3.1:1. WCAG asks for 4.5:1. `MUTED` is darker in light mode and lighter in dark mode, and the run button's text is dark in dark mode. A test now checks these ratios in both themes.
- **Controls.** Fields and buttons have borders you can see (3:1), a 2 px ring shows which one has the keyboard, and they have 8 px corners. Square corners on buttons were the strongest "old" signal after the missing icons. The picture's cells and nodes stay square. Gaps are on a 4 px grid.
- **The bars and the title.** The toolbar and status bar are a slightly different shade (`BAR`) that frames the work area. The legend is now a swatch for each mark, so it doesn't rely on colour alone (filled, outlined and dashed squares), and it hides when the window is too narrow. The title reads "● BSTInsert.java — Raphael" while there are unsaved changes. A Mac shows that in the close button instead, as Mac apps do. On Windows, FlatLaf draws the title bar, so it follows dark mode. On a Mac, Cmd+Q and the app menu's Settings… go through the same question about unsaved code and the same settings dialog.

## Stage 4b: A modern interface

- Why does the file dialog go through PowerShell inside WSL?

The system's own dialog looked right but didn't work right for me. Inside WSL, Raphael is a Linux program, so Java's "native" dialog is GTK's, and its Desktop button opened the empty Linux home folder instead of my Windows Desktop. A browser in the same position shows Explorer, so now Raphael does too. `WindowsFileDialog` writes a short PowerShell script that shows Windows' Open or Save dialog and prints the file chosen, runs it with `powershell.exe` (which WSL can start), and turns the Windows path into a Linux one with `wslpath` (`C:\Users\…` becomes `/mnt/c/Users/…`). The script is passed Base64-encoded (`-EncodedCommand`), so no file name with a quote or an accent can break it. While the dialog is open, Raphael's window ignores clicks but keeps repainting: a `SecondaryLoop` lets Swing go on handling events while the code waits for the answer. If anything fails, Raphael falls back to Java's own dialog. On a real Windows, Mac or Linux install, Java's dialog is already the system's own.

- Which fonts, and why two?

The monospace font (JetBrains Mono NL) stays for the code, the picture and the panels, where letters lining up matters. The interface itself (buttons, menus, dialogs) now uses a normal sans font, because monospace prose in dialogs was the biggest "old" signal left. On Windows and macOS that's the system's own font (Segoe UI and SF), which FlatLaf picks by itself. Linux's usual fonts look dated, so Raphael ships Inter there. It's free under the same licence as the code font (SIL OFL), and I approved adding it.

- How was the rest built?

Following the designer's spec (mockups in `docs/design/`):
- **The toolbar** has 32 px icon buttons in groups, a filled Run button that turns into a dark Stop while recording, and "Examples ▾". The icons are drawn with Java 2D in `Icons`, so they stay sharp at any scaling and need no image files or library. Each `Glyph` is an enum constant that overrides `draw()`. Every icon-only button has a name for screen readers.
- **One dialog style.** `Dialogs` uses the Builder pattern: `Dialogs.question(window, "Save changes to X?").body(…).buttons("Save", "Cancel", "Don't save").ask()`. The buttons say what they do, follow each system's order (main button first on Windows, last on macOS and Linux), and the keyboard starts on the safe one. The old "OK" that meant "throw my code away" is gone.
- **A first-run guide** (`EmptyState`): "Watch your program run", three numbered steps with their keys, and example chips. While recording, the picture shows "Recording… N steps", with a thin moving bar under the toolbar.
- **Settings** in two groups, each field with its unit and a line saying what it does. The theme is System / Light / Dark, so following the computer is a choice you can see.
- **A menu bar** (File, Edit, Run, View, Help) with every command and its shortcut. On a Mac it's the bar at the top of the screen. Toolbar buttons can be reached with Tab, and F6 moves between the toolbar, the code and the picture.
- **Dropping a `.java` file** onto the window opens it. `FileDrop` wraps the editor's own copy-and-paste handler and only takes over for files (the Decorator pattern). Windows can't drag files into Linux windows under WSL, so the guide doesn't promise it there.

