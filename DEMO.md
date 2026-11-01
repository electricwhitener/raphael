# Demo script (about 7 minutes)

Double-click **Run Raphael** on the Windows Desktop. It opens with the BSTInsert example loaded. Pressing run plays the program by itself; any step key pauses it.

**Keys:**
- `→` / `←`: step forward / back
- `Space`: play / pause
- `Alt+↓`: skip call
- `Alt+↑`: finish call
- `Home` / `End`: first / last step
- `Ctrl+Enter`: run
- `Ctrl` + mouse wheel: zoom

After a run, the keys step the program. Click in the editor to type again; there, the arrow keys move the cursor and `Alt` + arrow steps.

1. **BSTInsert: the tree grows.**
   - Press **run**. It plays by itself.
   - Point out the blue line (about to run) and the grey line (just ran).
   - Point out `root` and `n` above the nodes, and the call stack growing as `insert` calls itself.
   - Press `<` a few times: the newest node fades out again. Press **skip call** on line 24 to skip a whole insert.
   - *"Every step is a saved snapshot, so going back just shows an earlier one (the Memento pattern)."*
2. **ReverseList: the arrows flip.**
   - Pick it from **examples**, run, and step with `>` through the loop.
   - The nodes stay in place while `prev`, `curr` and `next` slide between them, and each `next` arrow swings around to point backwards.
   - *"Raphael recognised a linked list because `Node` has exactly one field of its own type."*
3. **BubbleSort: markers and swaps.**
   - Run it and step through.
   - `i` and `j` sit under the cells they index.
   - The line `arr[j] = arr[j + 1]` outlines the cell it writes (solid) and the one it reads (dashed). After it runs, the value flies across, and the changed cell glows amber.
   - *"Raphael finds out which variables are indexes by reading the code: `arr[j + 1]` means j indexes arr."*
4. **GridPaths: a DP table.**
   - Step to the line `paths[r][c] = paths[r - 1][c] + paths[r][c - 1]`.
   - The cell being computed is outlined, and the two cells it adds up are dashed. `r` and `c` mark the row and column.
5. **BuggyList: the bug is visible.**
   - Step to line 20.
   - After `curr.next = prev`, the arrow pulls back into its slot, and nodes 2, 3 and 4 fade away, because nothing points to them any more.
   - *"This reverse forgot to save `curr.next` first."*
6. **OffByOne: the crash explained.**
   - Run it.
   - The `i` marker walks to index 3, a red ghost cell appears past the end of the array, and the last step shows `ArrayIndexOutOfBoundsException` in red. The bug is `<=` instead of `<`.
7. **BTreeInsert: a B-tree split.**
   - Run it, then click line number 31 (`Node z = new Node();`) to jump to the first split.
   - Step on with `>`: the right half's key flies into the new node `z`, `z` is linked in, and the middle key flies up into the parent. Each node re-centres over its children.
   - *"Raphael spotted a B-tree because `Node` has an array of its own type (`Node[] children`)."*
8. **BankAccounts: OOP in the picture.**
   - Pick it from **examples** and run it.
   - The boxes are titled `Savings extends Account` and `Current extends Account`, and they show `id` and `balance`, which come from the parent class.
   - `a.deposit(10)` makes only `balance` flash. `a.interest()` runs a different method for each account (polymorphism), which you can see in the blue line.
   - `deposit(-1)` throws an exception, and the blue line jumps to `catch`.
9. **A syntax error.** Delete a `;` and press run. The line turns red, and hovering shows `';' expected`.
10. **An infinite loop.** Add `int x = 0; while (true) { x++; }` to `main` and run. It stops after 2,000 steps, and the window never freezes.
11. **For the room.** Ctrl+D switches to dark mode, Ctrl + and Ctrl - change the text size for a projector, and **keys** lists every shortcut.
12. **The teacher's own code.** Press **new** (say OK if it asks about unsaved code), then Ctrl+A and Ctrl+V to paste their program over the starter code, then Ctrl+Enter.
    - It should have one `public static void main(String[] args)` and be one file. A `package` line and any number of classes are fine.
    - If it reads input, a stdin box appears. Type the input there before running.
    - Each line is one step, so ask for one statement per line. `for (...) sum += a[i];` on a single line runs as one step.
    - Keep inputs small: the run stops at 2,000 steps. If a run takes too long, press **stop** or Esc, and you keep the steps recorded so far.

## Showing the OOP in the code
Open `docs/OOP-HIGHLIGHTS.md`. In VS Code, press Ctrl+Shift+V for a formatted view. It goes through each concept with real code, a comment on each line, and a link to the exact place in the file. It also has a suggested order and likely questions.
