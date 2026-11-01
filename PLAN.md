# Raphael — Plan

A solo OOP-in-Java course project, due **early November 2026**. The goal is learning: you must be able to explain every line.

## What Raphael is
Raphael is a desktop app that shows data structures changing as Java code runs.

1. The student types or pastes a single-file Java program into Raphael's editor, or opens a `.java` file.
2. They click **Run**.
3. Raphael asks the JDK's compiler (`javac`) to compile the code.
4. Raphael starts the program in a separate hidden process, in debug mode, and pauses it after every line.
5. At each pause it takes a **snapshot**: the current line, the variables, and every object in memory.
6. It draws each snapshot as **arrays, linked lists and binary trees**. Variables like `curr` appear as labels pointing at nodes.
7. The student steps forward and back through the run, or plays it as an animation.

### Who does what
- **The JDK** (installed on the computer) provides:
  - `javac`, the compiler
  - `java`, which runs programs
  - **JDI**, the toolkit for pausing and inspecting a running program
- **Raphael** directs them, then draws the results in its own window.

### The window
```
┌──────────────────────────────────────────────────────────────────────────────┐
│ [Open] [Save] [Examples ▾] [▶ Run] │ [⏮] [◀] [▶] [⏭] [⏯]  Step 37/120 ━━●━━  Speed ▾ │
├───────────────────────────────┬──────────────────────────────────────────────┤
│  1  class Node { int val;     │                 root                          │
│  2    Node left, right; }     │                  ▼                            │
│  3  public class BST {        │                 (8)                           │
│▶ 4    static Node insert(...) │               ╱     ╲                         │
│  5      if (n == null) ...    │            (3)       (10)                     │
│                               │           ╱   ╲         ╲                     │
│  (editable; current line      │        (1)    (6) ◄─curr (14)                 │
│   highlighted while stepping) │   arr: [ 1 | 3 | 6 | 8 |▓10▓| 14 ]            │
├───────────────────────────────┴──────────────────────────────────────────────┤
│ Call stack: main → insert │ Locals: n = Node#12, val = 6 │ Console output     │
└──────────────────────────────────────────────────────────────────────────────┘
```

### Features
- **Editor:**
  - Type, paste, open and save.
  - Line numbers.
  - The current line is highlighted while stepping.
  - Compile errors are marked on their line.
  - Syntax colouring is optional (Stage 9).
- **Timeline:** step forward and back, jump to the start or end, a slider, and auto-play at an adjustable speed.
- **Structures:**
  - Arrays (1D, and 2D grids such as DP tables).
  - Linked lists.
  - Binary trees and BSTs.
  - Any other object is shown as a plain box with its fields.
- **Animation:**
  - Nodes slide to their new positions.
  - New nodes fade in, and removed nodes fade out.
  - Changed values flash.
- **Panels:** call stack, local variables, console output, and an input box for `Scanner`.
- **Safety:**
  - A 2,000-step limit, so infinite loops stop with a warning.
  - Runtime exceptions are shown as the final step.

### Requirements and limits
- **Requirements:** a JDK must be installed, and the program must be one file with `main`.
- **Speed:** tracing is slow, which is why there is a step limit. Teaching-sized programs are fine.
- **Detection:** structures are recognised by the shape of their fields, so unusual classes fall back to plain boxes.

## Tech stack
- **Language:** Java, JDK 21.
- **Window:** Swing (built into Java), with FlatLaf for a modern look.
- **Build:** Maven (`pom.xml`).
- **Tests:** JUnit 5.
- **Dependencies:** FlatLaf, and JUnit for tests only. Everything else comes with the JDK.

## How each stage works
1. **Understand.** Learn the concept until you can explain it to a friend.
2. **Mini-exercise.** Write a tiny throwaway version yourself (20–60 lines) in `exercises/`.
3. **Build.** Build the real piece with Claude Code. Read every line, and ask "why?" until you can answer it.
4. **Explain.** Close the code and write a first draft of the class's purpose in your own words in `DECISIONS.md`. Claude then checks the draft, answers your questions, fixes mistakes and rewrites it as a complete, easy-to-read answer. The final version replaces your draft.

---

## Stage 0: Setup (Day 1)
- **Learn:** what a JDK is, what Maven and `pom.xml` do.
- **Do:**
  - Install JDK 21.
  - Connect VS Code to WSL and install the Java extensions in WSL (Red Hat "Language Support for Java" and "Debugger for Java").
  - Create the Maven project.
- **Done when:** Hello World runs.

## Stage 1: The Translator, compiling (Day 1–2)
- **Learn:**
  - Code is text, and `javac` turns it into runnable `.class` files.
  - Our program can call `javac` directly (`javax.tools.JavaCompiler`).
  - The `-g` option keeps variable names.
- **Exercise:** compile a string of Java code, then print "OK", or the error and its line number.
- **Build:** `SourceCompiler`, `CompileResult`.
- **Done when:** good code gives "OK", and a missing `;` reports the right line.

## Stage 2: The Remote Control, stepping (Day 3–5) ⚠️ most important
- **Learn:**
  - A debugger is a pause button plus a magnifying glass.
  - With JDI, one program can launch another in debug mode and control it.
  - Key words:
    - *launch*: start the program
    - *breakpoint*: a place to pause
    - *step*: run one line, then pause
    - *event*: the program telling us "I paused"
  - We step only through the student's code, not Java's internals.
- **Exercise:** launch a 5-line `Loop.java` and print `now on line N` for every line it runs.
- **Build:**
  - The `Tracer` interface, and the `JdiTracer` that implements it.
  - It must read the program's output on separate threads, otherwise the program freezes.
  - Add the 2,000-step limit.
- **Done when:** every example prints its line-by-line path, and `while(true)` stops at the limit.

## Stage 3: The Photographer, reading memory (Day 6–7)
- **Learn:**
  - Primitives (`int x = 5`) vs references (an arrow to an object). `null` is an arrow to nothing.
  - Every object has an ID.
  - Following arrows from the variables to find every object is a graph walk (BFS).
- **Exercise:** at each pause, print the locals, e.g. `i = 3, arr = [5, 2, 9]`.
- **Build:**
  - `Snapshot`: the line, and which methods are running.
  - `FrameSnapshot`: one method's local variables.
  - `HeapObject` → `ObjectNode` (an object with fields) and `ArrayNode` (an array's values).
  - `Value` → primitive, reference to object #N, or null.
- **Done when:** a linked-list program prints `Node#12{val=5, next→#15}`.

## Stage 4: The Photo Album, rewind (Day 8)
- **Learn:**
  - A `Trace` is a list of snapshots, and going back means showing snapshot `i-1`.
  - This is the **Memento** pattern.
- **Exercise:** a console menu where `n` = next and `b` = back, printing each snapshot.
- **Build:** `Trace`, which holds the snapshots, the program output and why the run ended.
- **Done when:** you can walk back and forth through a run in the console.

> **Checkpoint (end of week 1):** the whole engine works, without graphics.

## Stage 5: The Detective, recognising shapes (Day 9–10)
- **Learn:** shapes are recognised from fields.
  - Array → **array**; 2D array → **grid**.
  - 1 field of its own type → **linked list**.
  - 2 such fields → **tree**. If they're named `prev`/`next`, it's a list.
  - Anything else → **box**.
- **Exercise:** given a class's field types, print "list", "tree" or "box".
- **Build:**
  - `StructureDetector`.
  - `Structure`, with subclasses for Array, Grid, LinkedList, BinaryTree and Generic. This is **inheritance and polymorphism**.
- **Done when:** JUnit tests pass for each example.

## Stage 6: The Artist, drawing (Day 11–13)
- **Learn:**
  - The canvas is a grid with (0,0) at the top-left, and y grows downward.
  - Swing calls `paintComponent`, and we redraw the whole picture each time.
  - **Layout:**
    - Arrays and lists go left to right.
    - Trees: x comes from in-order position, y from depth.
- **Exercise:** a window that draws three circles joined by arrows. Then hard-code a 3-node tree.
- **Build:**
  - `StructureRenderer`, with subclasses for Array, LinkedList and BinaryTree.
  - Pointer labels for variables.
  - `Style`, the single place for colours, sizes and fonts. Use placeholder values for now.
- **Done when:** each example's snapshot is drawn correctly.

## Stage 7: The Window (Day 14–15)
- **Learn:**
  - The **Observer** pattern: `TraceModel` knows the current step and notifies every panel when it changes.
  - Tracing runs in the background (`SwingWorker`) so the window never freezes.
- **Exercise:** a button that changes a number, with two labels that both update through a listener.
- **Build:**
  - `MainWindow`.
  - `CodePanel`: the editable editor, with Open/Save, line numbers, current-line highlight and error marks.
  - `VisualPanel`, `ControlsBar`, `CallStackPanel`, `LocalsPanel` and `ConsolePanel`.
  - The Examples menu, and the FlatLaf theme.
- **Done when:** you can write or open code, press Run and step through it with the pictures updating.

## Stage 8: The Animator (Day 16–17)
- **Learn:**
  - Redraw about 60 times a second, moving things a little each frame.
  - `position = start + (end − start) × progress`, where progress goes from 0 to 1.
  - Fades work the same way, using transparency.
- **Exercise:** a circle that slides across the window when you click a button.
- **Build:**
  - `Animator`, which uses a Swing `Timer`.
  - `Diff`, which finds what changed between two snapshots: changed values flash, new nodes fade in, removed nodes fade out.
- **Done when:** a BST insert slides nodes into place, and stepping back reverses it smoothly.

## Stage 9: Design session (Day 18)
- **Decide:**
  - Colours, node shapes, arrows and fonts.
  - Animation speed.
  - Light or dark theme.
  - Optionally, syntax colouring (RSyntaxTextArea).
- **Change:** `Style`, and small parts of the renderers.

## Stage 10: Testing and presentation (Day 19–21)
- **Test:** 3–5 classmates try it on their own DSA code. Fix what confuses them, and note the feedback in `DECISIONS.md`.
- **Write:** a README with screenshots, and a demo script that includes a deliberately buggy linked list.
- **Viva prep:** be ready to explain where the idea came from, how it was developed, how it works, the environment and the dependencies.

## If time runs short (cut in this order)
1. Fade effects.
2. Call-stack panel.
3. 2D grid view.
4. Animation. Rewind with still pictures is still a complete project.

Stages 1–6 are never cut.

---

## Project layout
```
raphael/
  pom.xml, README.md, DECISIONS.md
  src/main/java/raphael/
    App.java
    compile/  SourceCompiler, CompileResult
    trace/    Tracer, JdiTracer, TraceLimits
    model/    Trace, Snapshot, FrameSnapshot, HeapObject, ObjectNode, ArrayNode, Value…
    detect/   StructureDetector, Structure + subclasses
    render/   StructureRenderer + subclasses, Style, Animator, Diff
    ui/       MainWindow, TraceModel, panels
  src/main/resources/examples/  BubbleSort, ReverseList, BSTInsert, FibDP, BuggyList
  src/test/java/raphael/        tests for compile, trace, detect
  exercises/                    your mini-exercises
```

## Final check
- `mvn test` passes.
- In the app:
  - **BSTInsert:** the tree grows, and stepping back shrinks it.
  - **ReverseList:** the `prev`/`curr` labels move and the arrows flip.
  - **FibDP:** the grid fills in order.
  - **BubbleSort:** swapped cells flash.
  - **BuggyList:** the bug is visible.
  - **A syntax error:** a clear message.
  - **`while(true)`:** stops at the limit.
- The window never freezes.
