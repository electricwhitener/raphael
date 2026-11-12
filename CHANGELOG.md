# Changelog

Versions follow [semantic versioning](https://semver.org): the first number changes when something stops working the way it did, the second when something is added, the third for fixes.

## Unreleased

**C++**
- C++ programs run, step by step, on Windows, macOS and Linux, with the same pictures as Java and C: classes drawn with their base class's fields inside them, an object through a base-class pointer drawn as what it really is (a Dog, not an Animal), references, `new` and `delete` (a deleted object faded, with "freed"), and the standard library's `vector`, `string`, `map`, `set`, `list`, `stack` (upright), `queue` and `priority_queue` (as a heap).
- `throw` and `catch` show the caught card, with the exception's real type and message ("out_of_range: index 5, but the size is 3").
- The Objects view draws C++ classes as UML, and the key moments explain C++ too: polymorphism (which `area()` runs), encapsulation (a private field, a setter that refuses), inheritance, recursion, and memory in C++'s words.
- Ten C++ examples: basics, references, classes, virtual methods, exceptions, vector and sort, a map, a heap and a stack, a linked list and a binary search tree.

**Working with folders and files**
- An Explorer in the side bar, beside the examples, for a program of several files: the folder's files as a tree, as in VS Code. A click shows a file; the file the run is in is the one lit.
- Raphael opens the tabs you had when you quit (saved files, folders and examples) the next time it starts.
- A file changed by another program (another editor, git) is read again when you come back to Raphael. If you have unsaved changes to it here, Raphael asks first.
- A new example, "A program in several files": an interface and two shapes in a package folder, used by Main.

**A more modern interface**
- Everything you can click now lights up under the mouse: the examples side bar's rows, the welcome page's categories, the boxes in the toolbar, the line numbers you can click, the slider. Run gets darker under the mouse; it used to turn pale grey and look switched off.
- A chosen row or cell is grey with a dark bar, not solid blue: blue means "about to happen", and Run is the one blue button. The language switches show the chosen language as a raised white pill.
- Rounded scroll bars and menus, the interface's own text (the status bar, the colour key, panel headings) in the sans font, panel headings and tabs on one line, and short hints in the call stack, variables and terminal before the first run.
- The fonts are the ones VS Code uses on your system: Consolas and Segoe UI on Windows (also when Raphael runs inside WSL, read from Windows' own fonts), Menlo and the system font on a Mac, the system's monospace font on Linux. IBM Plex Mono, inside the app, is the fallback.

**Fixes**
- ← → Home and End step the run wherever the keyboard is, except where you type (the code, the search box, the terminal while the program asks for input). After a click in the side bar, a table or a panel, they used to move around in there instead.
- A panel's × is a small, thin cross in a small square, as in VS Code, not a button as tall as the tab strip.
- Ctrl+N asks which language the new program is in (the last one is lit, so Ctrl+N then Enter is quick).
- One click on a group in the examples side bar opens or closes it, not only a click on its little arrow.
- Tooltips no longer pile up two or three at a time on Linux under Windows (WSL), and the welcome page no longer repeats an example's sentence as a tooltip under it. A menu item's sentence shows in the status bar instead of over the menu.
- A program waiting for input, or one long step, could be stopped as "an infinite loop" when the computer's clock jumped ahead (WSL does this every half minute or so). Time limits now use a clock that never jumps.

## 1.4.0 (2026-11-11)

**The terminal**
- The console is now a terminal. What the program prints shows up as it's printed, and a program that reads input asks for it there: type the line and press Enter, as in any terminal. The recording waits while the program waits for you, and Ctrl+D ends the input. This works for Java and C on Windows, macOS and Linux.
- Playing the run back, the terminal shows each line you typed where you typed it, from the step that read it on.
- The stdin box is gone. An example's input (its `// stdin:` line) is typed for you, one line each time the program asks; change or delete that line to type your own.

**The workspace**
- Close any panel with the × on its heading (the picture, the call stack, the variables, the terminal tabs). The new layout button in the toolbar brings a closed panel back, and so do a right-click on any panel's heading and View → Panels. Drag the dividers between panels. The picture can go below the code, and the panels down the right instead of along the bottom; Reset layout puts everything back. The layout is remembered.
- Ctrl + and Ctrl − zoom the whole window, as VS Code does: menus, toolbar, icons, panels, text and the picture. Ctrl 0 goes back to 100%. The zoom is remembered, and never goes past what the screen has room for. The code's own text size is in Settings.
- Space always plays and pauses. It used to press whichever button was clicked last. A button reached with Tab is pressed with Enter.
- Choosing in the moments or speed box gives the keyboard back, so Space and the arrow keys step the program at once; before, the box kept the keyboard and Space opened it again.

**Fixes**
- A C program with a node struct opens on the picture, as a Java one does (the binary search tree example opened on the calls view).
- The welcome page shows the examples of the language you're learning, or says it has none yet, instead of Java's.
- The examples side bar stays out of the welcome page, which has its own example browser, and comes back with your program.
- In a narrow window the toolbar gives way (shorter labels, then Skip and Finish call only in the Run menu) instead of cutting off its last buttons.

**Key moments**
- Fewer: a moment that only repeats an earlier one (every later call of a recursion, every malloc of a loop) is left out. Your own `// moment:` notes always stay.
- They no longer stop playing by default. A box in the playback bar, and the Run menu, choose: Moments off, Show moments, or Pause at moments.

## 1.3.0 (2026-11-11)

**C runs**
- Write a C program and press Run: Raphael compiles it with gcc and records every line through a debugger (gdb; lldb on a Mac), with every picture, view and explanation Java has.
- Structs are boxes linked by their pointers, so C's linked lists and trees are drawn as Java's are. A pointer to a variable reads `&x`, and a pointer into an array is a marker under its cell.
- `malloc`'d memory is an array of its length, memory not yet set shows `?`, and freed memory stays drawn, faded and labelled "freed", so a dangling pointer is plain to see. A second `free` of a block is caught.
- Crashes are explained: which pointer was NULL, freed or never set, which divisor was 0, which index was outside its array, which `assert` failed.
- Compile errors get plain hints (a missing `;` or `#include`, a wrong pointer type, an undeclared name, a function never written).
- Key moments for C: malloc made a block, free left pointers dangling, a function changed its caller's variable through a pointer, and `scanf("%c")` reading the newline a `%d` left behind (Java's `nextLine()` after `nextInt()` gets the same moment).
- The Windows installer includes a C compiler and debugger (WinLibs): the download grows to 159 MB, and they take 528 MB once installed. The Linux package depends on gcc and gdb; on a Mac, Apple's Command Line Tools (`xcode-select --install`).
- C says NULL, and a struct reads `struct Node {val=10, next=NULL}`. A missing `;` is marked on its own line, not the next one.
- The first C examples: hello, pointers and swap, factorial, a linked list, a binary search tree, and a NULL-pointer bug.

**Languages**
- Write C, C++ and Python programs as well as Java: they're coloured and saved as their own language (C runs; C++ and Python come next).
- New asks which language (Ctrl+N repeats the last one), the welcome page asks which language you're learning, a pasted program is recognised, and the language in the status bar changes it.

**Examples**
- An examples side bar (Ctrl+B): pick the language, search, and browse title, category, subcategory and case. One click previews an example in a tab the next click reuses; a double click keeps it.
- The examples are filed into subcategories: singly, doubly and circular lists; BST, AVL, red-black, B and B+ trees; heaps and hashing; graph traversals and shortest paths.

## 1.2.0 (2026-11-11)

**Key moments**
- Raphael explains what your program is doing at its key moments, with the run's own values, and playing pauses there:
  - a recursion calling itself, its base case, and every return on the way back ("factorial(n = 3) returns 3 * 2 = 6");
  - which catch took an exception and why;
  - a file created, written, or missing, and text that only reaches a file when the writer is closed;
  - a private field changed by its own method, and a setter that refused a value;
  - a child object that is also its parent's kind, and which override runs through a parent-type variable;
  - static fields shared by every object.
- Write `// moment: text with {variable}` on a line to add a key moment of your own.
- The slider marks each moment; Alt+PgDn and Alt+PgUp go to the next and previous one; Run → Pause at key moments switches pausing off.
- New tabs beside the console: Key moments (a list) and Trace table (a row per step, a column per variable).

**Views of the run**
- Four tabs above the picture, and a run opens the one that suits it:
  - **Calls**: the call stack as boxes, with what each call is waiting for and what came back; recursion that branches (Fibonacci, Towers of Hanoi) is drawn as the whole tree of calls, each with the value it returned.
  - **Objects**: the classes as a small UML diagram (parents above, a dashed line to an interface, private fields marked -) beside the objects alive now; the running method is outlined on its object and in its class.
  - **Files**: the input typed into the stdin box, word by word as the program reads it (so the line end nextInt() leaves behind shows), and every file in the program's folder, with the lines read so far faded and a missing file drawn as a ghost.
- 28 new examples: Java basics, equality, an immutable class, constructor chaining, default methods, lambdas, several catches, throw and throws, propagation, try-with-resources, reading and copying files, a CSV, word counts, and seven recursions (131 in all). An example can bring its own input with a `// stdin:` line.

**More structures**
- A heap's array is also drawn as its tree, and in heap sort the sorted end leaves the tree.
- Red-black trees in red and black (a soft rose, so it can't be mistaken for an error), AVL heights under each node, and parent links that no longer turn a tree into a box.
- Hash tables as a column of buckets with their chains; array stacks standing upright.
- Graphs drawn beside their adjacency list or matrix, with visited vertices, distances and weights.
- Union-find parent arrays drawn as their forest; B+ tree leaves linked; doubly circular lists with tidy wrap-around arrows.
- New examples: AVL insert, red-black insert (left-leaning and CLRS), a B+ tree, union-find.
- Fix: an exception passing through a try-with-resources was reported as caught on the try line.
- Fix: if Raphael is killed while a program is paused, the program now ends within a fifth of a second (it could take several seconds on a Mac).

## 1.1.1 (2026-11-10)

- Fix: in a program of one file, the line about to run wasn't marked in the code while stepping, and clicking a line number did nothing.

## 1.1.0 (2026-11-10)

**Works like an IDE**
- Tabs: each program has its own tab and its own run, so you can switch while one is recording. Ctrl+Tab switches, Ctrl+W closes, Ctrl+Shift+T reopens, and a right-click on a tab closes the others or shows the file's folder.
- The Welcome page is a tab, and the logo at the left of the toolbar takes you home.
- File → Open Folder: a folder of `.java` files, packages included, runs as one program, with a tab for each file. While stepping, the code follows the program from file to file.
- File → New Window.
- A find and replace bar (Ctrl+F, Ctrl+H) with match case, whole word and regular expressions; Go to Line (Ctrl+G) and Ln, Col in the status bar.
- A command palette (Ctrl+Shift+P) and Quick Open (Ctrl+P) for files and examples.
- Brackets and quotes close themselves, the pair at the cursor is outlined, and Shift+Alt+Down duplicates a line.
- A compile error in the console takes you to its line.
- Fix: two programs started at the same moment could fail with "Not listening".
- Fix: a program in a package named `raphael` wasn't traced.


**A new logo**: Raphael's own character, on a rounded tile that suits light and dark alike.

**Files and saving**
- Save a recording (File → Save recording): the program and every step in a `.raphael` file that opens and plays without running anything. Installing Raphael makes double-clicking one open it.
- Export the steps as a CSV table for a spreadsheet: each step's line, variables and output.
- File → Open recent, and recent files on the welcome page.
- Programs that use files run in a folder of their own. A new files panel shows each file at every step, with changes in amber, and can add input files for the program to read. Three new examples: writing and reading a file, a missing file, appending.

**A welcome page and 100 examples**
- Raphael opens on a welcome page, like an IDE's: start a new program, open a file, paste one, or pick an example. File → Close (Ctrl+W) goes back to it.
- 100 short, commented examples in 15 groups: classes and objects, the OOP principles, exceptions, strings and collections, ten sorting algorithms, searching, recursion and backtracking, dynamic programming, arrays, every common linked-list operation, stacks and queues, trees and their traversals, heaps and hashing, graphs (BFS, DFS, shortest paths, Dijkstra, topological sort), and programs with a bug to find.
- The Examples menu groups them the same way.
- An empty `char[]` cell shows `'\0'` instead of a broken character.

**Exceptions, explained**
- Caught exceptions are shown too, on the first line of their `catch`: what was thrown, where it was thrown and caught, a red bracket in the line numbers from the `throw` to the `catch`, and the methods it left, greyed in the call stack.
- Plain explanations for the common exceptions, built from Java's own messages: "`curr` was null, so the line couldn't read its field `next`", or "The array has 5 cells, numbered 0 to 4, so there's no position 5."
- Hints for the common compile errors, like "Java doesn't know the variable `totl`: check its spelling (capitals count)."
- A new Exceptions example: a checked exception of your own, `finally`, and one exception wrapped in another.

## 1.0.0 (2026-11-05)

The first public release.

**Running your code**
- Paste, open or drop any single-file Java program and press Run. Raphael compiles it, runs it in a second JVM and records every line.
- Step forward and back, play at any speed, skip or finish calls, or jump to a line.
- Arrays, grids, linked lists, binary trees, B-trees, tries, Java's collections and any other object are drawn and animated as they change, with `i`/`j` markers and outlines on the cells each line reads and writes.
- Compile errors, crashes, missing input, endless loops and runaway recursion each get a plain message at the right line.

**Made to be relied on**
- Installers for Windows, macOS (Apple Silicon and Intel) and Linux, each with its own Java inside. CI installs and self-tests every one.
- The program you run gets at most 256 MB of memory and never outlives Raphael, even if Raphael is killed.
- Your code is autosaved and offered back after a crash. Files are saved so they can never be left half-written.
- Unexpected problems are caught, explained and logged on your computer. Nothing is sent anywhere (see [PRIVACY.md](PRIVACY.md)).
- Recording is fast: building a 500-node list (2,000 steps) takes about 2 seconds.
- Over 220 tests run on Windows, macOS and Linux on every change, plus 900 repeated runs per system every night.

**A modern interface**
- The system's own file dialogs (Windows' own even inside WSL), a menu bar, an icon toolbar, a first-run guide with examples, light and dark themes that can follow the system, and a settings window.
- Shortcuts use Cmd on a Mac. Everything can be reached from the keyboard.
