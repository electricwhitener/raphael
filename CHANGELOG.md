# Changelog

Versions follow [semantic versioning](https://semver.org): the first number changes when something stops working the way it did, the second when something is added, the third for fixes.

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
