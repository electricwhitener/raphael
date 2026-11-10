# Changelog

Versions follow [semantic versioning](https://semver.org): the first number changes when something stops working the way it did, the second when something is added, the third for fixes.

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
