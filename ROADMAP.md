# Raphael roadmap: from course project to product

## Context
The course submission is done and the teacher liked it. He asked the user to:
- refine Raphael and add features
- show more Java: exceptions, file handling, catching errors
- test the edge cases
- write a detailed report (for the department, the college magazine and a certificate)

The user now wants Raphael to be **a real, production-grade product**:
- no bugs
- smooth on any machine, not just their WSL laptop
- every situation handled
- better than the competitors

**Decisions (2026-11-04):**
- **Form:** a **desktop app** first (Windows, macOS, Linux), and **IDE plugins** later.
- **Licence:** **open source** (MIT, Apache 2.0 or GPL; chosen at the release stage).
- **Language:** **Java only** for now. Other languages are a future version, with no commitment.
- **Features:** all of them:
  - exceptions in the student's code
  - files and saving
  - more structures
  - learning modes

  PNG and GIF export is low value, so it comes late and stays small.
- **Rules relaxed:** new classes and new libraries are allowed. Each stage is still explained before it's built, and each library is named with a reason first.
- **Report:** start now and grow it stage by stage. Choose the format at the end.

**Competitors (researched 2026-11-04):**

| Tool | Strengths | Gaps Raphael targets |
|---|---|---|
| **JavaWiz** (JKU Linz; v2.4.0; awards; ICPC 2025 paper; GPL-3.0) | VS Code and IntelliJ plugins; records the run so you can step back; 10 synchronised views (flowchart, memory, trace table, array, list/tree, sequence diagram, input, streams, history, console); animates array assignments and list/tree insertions | Needs an IDE **and JDK 25+** installed, with `java` and `javac` matching; no play or speed control stated; no B-tree, grid or trie layouts; no index markers; its streams view has open bugs |
| **Python Tutor** | Web; 5 languages; huge user base | Stops at 1,000 steps (about 300 for C); generic boxes and arrows; online only |
| **jGRASP** | Recognises lists, trees, stacks, queues and hash tables | A dated IDE; viewers opened by hand; steps forward only |
| **VisuAlgo** | Polished animations | Only its own built-in algorithms |

**Raphael's position:** paste any Java program and get a **textbook-quality animated picture**, with **no setup**, **offline**, and **plain-English explanations** when something goes wrong.

**What it can already do that the others can't:**
- B-tree, grid and trie layouts
- `i`/`j` markers and read/write outlines from reading the code
- spring animation
- 2,000 steps, with a live **stop**

**What's Raphael's own and what's borrowed (checked 2026-11-04):**
- **Our own:**
  - all the animation: `Spring`'s math, `Animator`'s fades, swings and flying values, `Diff`
  - all the layouts: trees, B-trees, lists, grids
  - structure detection, `IndexUsage`
  - the drawing logic in `ScenePainter`
- **The JDK's building blocks:**
  - `javax.tools` compiles
  - JDI pauses the program and reads its memory
  - Java 2D draws lines and text
  - Swing gives the window and the timer ticks
- **The only outside library:** FlatLaf, which styles the buttons and menus.
- No animation, layout or online API is used. (JavaWiz, by contrast, uses a web frontend and the d3-graphviz library for its memory view.)

**Found in the code today:**
- `MainWindow.startFolder()` starts at a WSL-only `/mnt/c/Users/…/Desktop`.
- 20 shortcuts are hard-coded as `"ctrl …"`, which is wrong on macOS, where shortcuts use Cmd.
- 7 `toLowerCase()`/`toUpperCase()` calls follow the computer's language setting (the "Turkish i" bug).
- `JdiTracer.launch()` sets no memory limit on the debuggee.
- Nothing kills the debuggee if Raphael quits or crashes.
- Recording a 500-node list takes about 35 seconds.
- Only uncaught student exceptions are traced (`JdiTracer.java:140`, `notifyCaught=false`).
- Nothing is `Serializable`, there's no log file, and the only custom exception is `TraceException`.

## How each stage runs
1. Explain the stage first: what, why, and which concepts it shows.
2. Build it, with tests.
3. `mvn test` passes on all three operating systems (CI, from Stage 1), then `scripts/build-app.sh`.
4. Check it in the real window; the critic agent reviews visual stages.
5. Update CLAUDE.md Progress, README, DECISIONS.md and the report chapter.
6. Post a short "for your teacher" note.

## Part 1 — Production-grade foundation (first)

### 0. Set up
- CLAUDE.md:
  - add a "Product" section with the decisions above
  - relax the old rules
  - set Next
- Report skeleton in `docs/report/`, one Markdown file per chapter:
  1. Introduction
  2. Competitors
  3. Background (JDI, javac)
  4. Architecture
  5. OOP and design
  6. Features
  7. Exceptions and files
  8. Quality and testing
  9. Results
  10. Limits and future work
- Draft chapters 1–5 from the README, OOP-HIGHLIGHTS and DECISIONS.

### 1. Works on any machine
- **CI from day one:** GitHub Actions runs `mvn test` on `windows-latest`, `macos-latest` and `ubuntu-latest` on every push (Linux uses a virtual screen, xvfb).
- **Fix the platform assumptions:**
  - `startFolder()`: use the OS's own Desktop or Documents folder, and keep the WSL path only when running inside WSL.
  - Shortcuts: `Toolkit.getMenuShortcutKeyMaskEx()`, so a Mac uses Cmd; key-help text per OS.
  - `toLowerCase(Locale.ROOT)` everywhere.
  - UTF-8 forced for source files, and Windows paths and line endings handled.
  - HiDPI and scaling checked on Windows at 150%, and on a Mac Retina screen via CI screenshots.
  - Dark mode follows the OS on first launch.
- **Launchers:** native launchers replace `Run Raphael.bat`, and WSL becomes optional.

### 2. Bulletproof tracing
- **The debuggee:**
  - a memory cap (`-Xmx256m`, configurable)
  - a shutdown hook and a kill on quit, so no stray JVMs are left behind
  - kill and clean up if Raphael crashes mid-run
- **Edge-case corpus** in `src/test/resources/edge/`, run by a parameterised `EdgeCaseTest`:
  - empty `main`, `System.exit` mid-run, threads, generics, lambdas and streams
  - inner, anonymous and static nested classes, interfaces with defaults, records and enums in student code
  - 10,000-element arrays, unicode names, cycles, null everywhere, `StackOverflowError`
  - `Integer` caching, `long`/`double` edge values
  - `Scanner` running out of input, very long lines, CRLF, tabs
  - `package` lines, several top-level classes
  - an infinite loop with no output, an infinite loop with heavy output, huge string output
- **Soak test:** each example run 100 times in CI, and every run must give the same steps (this guards against the old 1-in-60 timing bug class).
- Fix every bug found, each with its own test.

### 3. Raphael catches its own errors
- **Exception hierarchy:** `RaphaelException` (checked) → `TraceException`, `RunFileException`, `CompileSetupException`. Shows chaining with `cause`, checked vs unchecked, and multi-catch.
- **Safety net:**
  - `Thread.setDefaultUncaughtExceptionHandler` plus a handler for Swing's UI thread
  - a friendly dialog with "copy details" and "open log folder"
- **Logging:** `java.util.logging` to the OS's app-data folder, with rotation. Nothing is sent anywhere; this goes in the privacy note.
- **Draft autosave** and recovery after a crash.
- **Settings dialog** for the step limit, the timeout, the debuggee memory, the theme and the text size (stored in `Preferences`, as now).

### 4. Fast
- Profile recording first. The target is a 500-node list in under 5 seconds (it takes about 35 today).
  - Candidates: read only the objects that changed since the last step, batch the JDI calls, and cache class layouts.
- Memory: a long run's heap kept compact, with a test that a 2,000-step run stays under a set size.
- Animation: steady 60 fps on a low-end laptop, measured by a frame-time test.
- Startup time measured, with a target under 2 seconds.

### 5. Installers
- `jlink` makes a small private Java runtime with only the modules Raphael needs:
  - `java.desktop`, `java.compiler`, `jdk.compiler`, `jdk.jdi`, `jdk.jdwp.agent`, `java.prefs`, `java.logging`
- `jpackage` builds `.msi` (Windows), `.dmg` (macOS, Apple Silicon and Intel) and `.deb` plus `.AppImage` or `.rpm` (Linux), all built in CI.
- **Smoke test in CI:** install each package, launch it, and trace one example.
- **Code signing** stops the "unknown publisher" warnings on Windows and macOS, but it costs money (an Apple developer account, a Windows certificate). This is the user's decision at this stage; free options like SignPath for open source will be checked.
- Replaces the untested `scripts/build-windows.sh`.

### 6. First public release (v1.0)
- Choose the licence, then make the repo public.
- README for the public, with a GIF at the top; a GitHub Pages site; a user guide; a privacy note.
- CHANGELOG, CONTRIBUTING, issue templates and semantic versions.
- An update check against GitHub Releases ("a new version is available").

## Part 2 — Features (each one a 1.x release)

### 7. Exceptions in the student's code
- **Trace caught exceptions too:**
  - `notifyCaught=true`, keeping only exceptions thrown or caught in the student's classes
  - the catch line from `ExceptionEvent.catchLocation()`
- **Model:** a `ThrownException` record (type, message, throw line, catch line, frames unwound) on `Snapshot`.
- **Shown as:**
  - a red-outlined exception box
  - a gutter arrow from the throw to the `catch`
  - unwound frames fading out of the call stack
- **Plain-English explanations:**
  - an `ErrorExplainer` abstract class, with one subclass per common exception and a factory, `forType()`
  - each subclass uses the snapshot, for example "`curr` was null when line 14 read `curr.next`"
- **Compile-error hints:** for `';' expected`, `cannot find symbol`, `incompatible types`, `missing return statement` and `unreachable statement`.
- **Examples:** `Exceptions` (a custom exception, finally, rethrow).

### 8. Files and saving
- **Save and open recorded runs** (`.raphael`):
  - serialization with a `serialVersionUID`
  - streams stacked as `ObjectOutputStream(GZIPOutputStream(BufferedOutputStream(FileOutputStream)))` (the Decorator pattern)
  - a magic header and a version number
  - bad files throw `RunFileException`
- **File associations:** double-clicking `.java` or `.raphael` opens Raphael.
- Recent files, and a CSV step log (`PrintWriter`).
- **Files used by student programs:**
  - the program runs in a temp working folder, and a files tab lets the user add input files
  - each step records the small text files there, shown in a Files panel with changes in amber
  - example: `FileIO` (try-with-resources, `FileNotFoundException`)

### 9. More views, all synchronised (match JavaWiz, then beat it)
Each view is one more `TraceListener` on `TraceModel`, the Observer pattern Raphael already uses, so a view is added without changing the others. **Clicking anything in any view jumps to that step:** a table row, a history event, a flowchart box, an object in the picture.
1. **View layout:** tabs over the picture area (picture, memory, flowchart, sequence) and tabs at the bottom (variables, trace table, history, console, input).
2. **Trace table:**
   - the paper hand-trace students do in class: one row per step, one column per variable, changed cells amber
   - the whole run's history at a glance
   - cheap to build, because `Trace` already has every value
3. **History:**
   - a searchable list of calls, constructors, returns and assignments, worked out from what changed between snapshots (`Diff`, `FrameSnapshot`)
   - type `insert` or `count =`, then click to jump
4. **Memory view:**
   - stack frames, statics and the heap with arrows, in a textbook memory diagram
   - detail levels: beginner (just your objects), detailed (every field), internals (inside Java's collections)
5. **Sequence diagram:**
   - which object called which method, over time (a UML sequence diagram)
   - needs `this` recorded for each frame (`StackFrame.thisObject()` in `HeapReader.readFrames()`)
   - shows the OOP directly: objects sending messages to each other
6. **Flowchart:**
   - a flowchart of the current method built from javac's own syntax tree (`com.sun.source.tree`, already in the JDK), with the active box lit and values on hover
   - collapse and expand
7. **Input view:** stdin with the consumed part greyed, by reading `Scanner`'s position inside the paused program.
8. **Streams view (stretch, last):** each element's path through `filter`, `map` and `collect`. JavaWiz's own version has open bugs, so it gets done only if it can be done properly.

### 10. More structures
1. Hash tables (bucket chains)
2. Stacks and queues
3. Heaps (an array plus a tree view, found from `2*i+1` index patterns)
4. Graphs (a node class with neighbours, or an adjacency list or matrix; circle and layered layouts; visited colouring)

Each has an example and tests. Files: `detect/StructureDetector`, new `Structure` and `StructureRenderer` subclasses, and `StructureRenderer.forStructure()`.

### 11. Learning modes
1. **Predict the next value:** uses `Diff.between()` and keeps a score.
2. **Line heat map and access counts:** uses `IndexUsage.accessesOnLine()`.
3. **Growth chart (stretch):** run with n = 2…32 from stdin, and plot steps against n, n log n and n².
4. **Two programs side by side**, stepped together.

JavaWiz's "pedagogical execution traces" (explanations plus questions inside a trace) are still a work-in-progress research idea in their papers. Shipping this stage first is a clear lead.

### 12. Exports and sharing (small)
- The picture as PNG (`ImageIO`), and the run as a GIF (for the README, slides and the magazine).

## Part 3 — Reach

### 13. IDE plugins
- Split the build into modules:
  - `raphael-core` (compile, trace, model, detect, render)
  - `raphael-app` (the window)
- **IntelliJ plugin:** IntelliJ's own interface is Swing, so Raphael's picture and controls can sit inside an IntelliJ tool window, reusing the panels.
- **VS Code extension:** a small TypeScript extension that adds "Visualize with Raphael" above `main` and opens the desktop app on that file. VS Code can't host Swing, so the desktop app does the drawing.

### 14. Report
- Finish the chapters with screenshots, the coverage and performance numbers, the edge-case table and the competitor comparison.
- Choose docx or PDF then.

## Future version (not now)
Other languages reuse everything after the tracer; only `SourceCompiler`, `JdiTracer` and `HeapReader` depend on Java's tools.
- C/C++ would help the user's university most (pointers) and is the hardest (gdb).
- Python is the easiest, and a cheap way to build the multi-language layer first.

**Known limit to address there or earlier:** pasted code runs with the user's own permissions. Java 21 has no portable sandbox, so Raphael runs it in a temp folder and says so in the docs.

## Verification
- Every stage: CI is green on Windows, macOS and Linux; `EdgeCaseTest`, `ExamplesTest` and the soak test pass; the stage's example runs in the real window.
- Stage 2: quitting Raphael mid-run leaves no `java` process behind, checked with `ps` or Task Manager in CI.
- Stage 3: a forced UI-thread exception shows the dialog and writes the log.
- Stage 4: the benchmark numbers are recorded in CI and stay under their targets.
- Stage 5: each installer installs, launches and traces an example on a clean CI machine.
- Stage 8: save → reopen gives identical steps, and a truncated file gives a clear message.
