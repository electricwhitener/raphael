# 4. Architecture

## 4.1 The pipeline
Raphael turns the editor's text into an animated picture in six stages. Each stage is a separate package with one job, and each hands the next a plain data object.

```
editor text
   │  SourceCompiler.compile()      compile/   javac with -g, into a temp folder
   ▼
CompileResult                                  .class files, errors, the class that has main
   │  JdiTracer.trace()             trace/     second JVM, a breakpoint on every line
   ▼
Trace = [Snapshot, Snapshot, …]     model/     one Snapshot per line run
   │  StructureDetector.detect()    detect/    array? grid? list? tree? box?
   ▼
Structures
   │  SceneBuilder.build()          render/    renderers lay out shapes, add markers
   ▼
Scene per step                                 shapes with stable keys ("#12", "#7[2]", "var:curr")
   │  Animator + ScenePainter       render/    springs move each shape; Java 2D draws it
   ▼
VisualPanel in the window           ui/        every panel follows the TraceModel
```

| Package | Files | Job |
|---|---|---|
| `compile` | 2 | Compile the source and find the class with `main` |
| `trace` | 6 | Run the program under JDI and record every step |
| `model` | 12 | Plain data: values, objects, snapshots, the whole run |
| `detect` | 10 | Recognise structures; read the code for array indexes |
| `render` | 18 | Lay out, animate and draw |
| `ui` | 12 | The window and its panels |

Because the stages only meet through these data objects, each can be tested alone: `StructureDetectorTest`, for example, builds snapshots by hand and never starts a second JVM.

## 4.2 Compiling
`SourceCompiler.compile(source)` writes the text to a temporary folder and compiles it with `javax.tools` and `-g` (section 3.2). It returns a `CompileResult`: whether it worked, the errors with their line numbers, the class folder, and the name of the class that has `main`.

Pasted code has no file name, so Java's file rules ("a public class must be in a file of the same name") would reject most programs. Before compiling, Raphael replaces the top-level `public` and `package` words with the same number of spaces, so every line and column number stays right. It also removes invisible characters that web pages add when code is copied.

## 4.3 Tracing and the model
`JdiTracer` launches the program in a second JVM, sets a breakpoint on every line of the student's classes, and waits for events (section 3.3). At every breakpoint, `HeapReader` copies the paused program into a `Snapshot`:
- It starts from the local variables of the running methods and from the static fields.
- It follows every reference to the objects behind them, then to their fields, and so on. This is a breadth-first search, with a "seen" set so a cycle can't loop forever.
- Primitives and strings are stored as text. A reference is stored as the object's id, so one object can be pointed at from many places.
- Java's collections (`ArrayList`, `HashMap`, …) are read by calling `toArray()` inside the paused program, and stored as plain arrays.

The `model` package holds these copies. It has no JDI types in it:

| Class | Holds |
|---|---|
| `Value` → `PrimitiveValue`, `ReferenceValue`, `NullValue` | What a variable, field or cell holds: `5`, `#12` or `null` |
| `HeapObject` → `ObjectNode`, `ArrayNode` | An object with fields, or an array |
| `FrameSnapshot` | One running method: its name, line and local variables |
| `Snapshot` | Everything at one pause: the frames, the heap, the statics, the output so far |
| `Trace` | The whole run: every snapshot, the output, and why it ended (`EndReason`) |

Snapshots are never changed after they are made. Stepping back is therefore just showing the previous snapshot, with no re-running (the Memento pattern, chapter 5).

**Safety limits** (`TraceLimits`) make sure every run ends:
- 2,000 steps at most, which also ends infinite loops
- a 5-second limit when the program stops responding
- at most 300 objects and 64 cells per array in each step
- only the newest 32 method calls read in each step, so recursion with no base case ends quickly
- a stop button that keeps the steps recorded so far

Two `OutputDrainer` threads read the program's output as it is printed, because a full output pipe would freeze the program inside `println`.

## 4.4 Recognising structures
`StructureDetector` decides what each object is from the types and names of its class's fields, never from the program's name:

| Fields of class `Node` | Structure |
|---|---|
| one field of type `Node` (`next`) | linked list |
| `prev` and `next` | doubly linked list |
| two fields of type `Node` (`left`, `right`) | binary tree |
| an array of `Node` (`Node[] children`) | multi-way tree, such as a B-tree |
| an array of arrays (`int[][]`) | grid |
| anything else | box with its fields |

So any student's node class works, whatever its data fields are called. Half-finished structures are kept too: during a list reversal, both chains are drawn.

`IndexUsage` reads the program's text to find how arrays are indexed. `arr[j + 1]` means `j` indexes `arr`; `paths[r][c]` means `r` indexes rows and `c` columns. It also finds which cells each line reads and which it writes. These become the `i`/`j` markers and the read/write outlines.

## 4.5 Laying out a step
`SceneBuilder` turns every snapshot into a `Scene`, which is a list of shapes (`SceneNode`) and arrows (`SceneEdge`) with exact positions. One renderer per kind of structure, each a subclass of `StructureRenderer`, does the layout:
- **Arrays:** cells in a row; small arrays share a row.
- **Lists:** each node keeps its left-to-right place for the whole run (`ListOrder`), so a reversal turns the arrows around instead of moving the nodes.
- **Binary trees:** x from the in-order position, y from the depth, so a BST reads in sorted order.
- **B-trees:** rows of keys, children underneath, each parent centred over its children.
- **Other objects:** boxes titled with the class and its parent class (`Savings extends Account`).

Before any step is laid out, `SceneBuilder` measures the whole run, so cell widths and spacing never jump between steps.

Every shape has a key that stays the same across steps. `#12` is object 12, `#7[2]` is cell 2 of array 7, and `var:curr` is the label of the variable `curr`. Matching keys is how the animator knows which shape moved.

## 4.6 Animating
`Diff.between(before, after)` compares two scenes key by key and sorts every shape into added, removed, moved or changed. `Animator` then moves every shape towards its new place with a spring (`Spring`). The spring is critically damped, meaning it has just enough friction that it never overshoots:

```
x(t) = target + (a + b·t)·e^(−ω·t),   where a = start − target and b = speed + ω·a
```

The equation is solved exactly for each frame, so the motion is the same at any frame rate. When a new step changes a target mid-motion, the shape keeps its current speed and bends towards the new place, so fast stepping flows instead of jerking. On top of the springs:
- new shapes fade in, and removed ones fade out faster
- changed values roll and glow amber
- an arrow whose target changed swings around its start like a clock hand
- a value copied between cells (`arr[j] = arr[j + 1]`) flies across

Text is drawn as glyph outlines at exact fractional positions, so it doesn't wobble while it moves. `ScenePainter` draws each frame with Java 2D.

All of this logic is Raphael's own code. The JDK provides only the drawing primitives and the timer.

## 4.7 The window
`MainWindow` (a `JFrame`) is made of panels:
- `CodePanel`: the editor, with colours, line numbers, and the next and previous lines marked
- `VisualPanel`: the picture, centred and scaled to fit, with one scale for the whole run
- `ControlsBar`: step, skip call, finish call, the slider, play and speed
- `CallStackPanel`, `LocalsPanel` and `ConsolePanel`: the running methods, the variables, and the output with a stdin box

`TraceModel` holds the loaded run and the current step. Every panel implements `TraceListener` and registers with the model. Whatever changes the step (a key, a button, the slider, play) calls the model, and the model tells every panel. No panel knows about any other (the Observer pattern, chapter 5).

Compiling, tracing and laying out run on a background `SwingWorker`, so the window never freezes. Results come back to the UI thread in `done()`.
