# Raphael's C tracer, run inside gdb: gdb -q -batch -nx -x raphael_gdb.py program
# It stops before every line of the student's files and prints one JSON line per step,
# starting "@@R ", on gdb's stdout; Raphael's CTracer turns them into Snapshots.
# The program's own input and output go to files (the shim, raphael_rt.c, opens them), so they
# never mix with these lines.
#
# Settings, from the environment:
#   RAPHAEL_SRC        the folder the program's files are in
#   RAPHAEL_RUN        the folder the program runs in (its files are reported when they change)
#   RAPHAEL_STDOUT     the file the program's output goes to (its size is reported at each step)
#   RAPHAEL_STOP       a file that, once it exists, ends the run
#   RAPHAEL_MAX_STEPS, RAPHAEL_MAX_FRAMES, RAPHAEL_MAX_OBJECTS, RAPHAEL_MAX_CELLS
#
# Lines: {"kind": "start", "pid": n}, then {"kind": "step", ...} for each step, then
# {"kind": "end", "reason": "finished" | "steps" | "crash" | "stopped", ...}.
# Values: {"p": "text"} (plain), {"r": id} (an object in "heap"), {"n": 1} (NULL).
# Heap objects: {"k": "o", "type", "fields", "ftypes"} (a struct) or {"k": "a", "type", "elem", "items", "len"} (an array).
import bisect
import gdb
import json
import os
import struct

SRC = os.path.realpath(os.environ.get("RAPHAEL_SRC", "."))
RUN = os.environ.get("RAPHAEL_RUN")
STDOUT = os.environ.get("RAPHAEL_STDOUT")
STOP = os.environ.get("RAPHAEL_STOP", "")
MAX_STEPS = int(os.environ.get("RAPHAEL_MAX_STEPS", "2000"))
MAX_FRAMES = int(os.environ.get("RAPHAEL_MAX_FRAMES", "32"))
MAX_OBJECTS = int(os.environ.get("RAPHAEL_MAX_OBJECTS", "300"))
MAX_CELLS = int(os.environ.get("RAPHAEL_MAX_CELLS", "64"))
MAX_FILES, MAX_FILE_BYTES = 20, 20000

out = os.fdopen(os.dup(1), "w", buffering=1, encoding="utf-8")


def send(message):
    out.write("\n@@R " + json.dumps(message) + "\n")  # on a line of its own, even after half a line of gdb's


for setting in ("pagination off", "confirm off", "width 0", "print elements 200",
                "startup-with-shell off", "debuginfod enabled off"):  # not every gdb has the last two
    try:
        gdb.execute("set " + setting, to_string=True)
    except gdb.error:
        pass

# ---- the student's files ----

lines_of = {}  # a file of the program (relative to SRC) → its lines, or None if it isn't one


def student_file(symtab):
    """The program's own file this code is in ("main.c", "lib/list.c"), or None."""
    if symtab is None:
        return None
    try:
        full = os.path.realpath(symtab.fullname())
    except (gdb.error, RuntimeError):
        return None
    if not full.startswith(SRC + os.sep):
        return None
    rel = os.path.relpath(full, SRC).replace(os.sep, "/")
    if rel not in lines_of:
        try:
            with open(full, encoding="utf-8", errors="replace") as f:
                lines_of[rel] = f.read().split("\n")
        except OSError:
            lines_of[rel] = []
    return rel


def text_of(rel, line):
    lines = lines_of.get(rel) or []
    return lines[line - 1].strip() if 0 < line <= len(lines) else ""


# ---- the shim's table of malloc'd blocks ----

UNSET = 0xBE  # what the shim fills new memory with (raphael_rt.c)


class Blocks:
    """The blocks malloc gave the program, read from the shim's table at each step."""

    def __init__(self):
        self.starts, self.blocks = [], []  # sorted by address: (start, size, freed)
        try:
            count = int(gdb.parse_and_eval("(int) raphael_block_count"))
            table = int(gdb.parse_and_eval("(long long) &raphael_blocks"))
        except gdb.error:
            return  # no shim (not compiled by Raphael)
        if count <= 0:
            return
        raw = bytes(gdb.selected_inferior().read_memory(table, count * 24))
        rows = sorted((a, n, f) for a, n, f in struct.iter_unpack("<QQi4x", raw) if a)
        self.blocks = rows
        self.starts = [r[0] for r in rows]

    def at(self, address):
        """The block this address is in (start, size, freed), or None; the newest if several."""
        i = bisect.bisect_right(self.starts, address) - 1
        while i >= 0:
            start, size, freed = self.blocks[i]
            if start <= address < start + max(size, 1):
                # the same address can be freed and given out again: the live one wins
                if freed and i + 1 < len(self.blocks) and self.blocks[i + 1][0] == start:
                    i += 1
                    continue
                return self.blocks[i]
            if start + size < address - 4096:
                return None
            i -= 1
        return None

    def starting_at(self, address):
        block = self.at(address)
        return block if block is not None and block[0] == address else None


def unset(v):
    """Whether v lies in malloc'd memory nobody has written yet."""
    try:
        if v.address is None:
            return False
        address = int(v.address)
        if STEP_BLOCKS[0].at(address) is None:
            return False
        data = bytes(gdb.selected_inferior().read_memory(address, v.type.sizeof))
        return len(data) > 0 and all(b == UNSET for b in data)
    except (gdb.MemoryError, gdb.error, RuntimeError):
        return False


NO_BLOCKS = Blocks.__new__(Blocks)
NO_BLOCKS.starts, NO_BLOCKS.blocks = [], []
STEP_BLOCKS = [NO_BLOCKS]  # the table for the step being read


# ---- values, structs and arrays ----

ids = {}  # (address, type) → the object's id, kept for the whole run


def object_id(address, type_name):
    key = (address, type_name)
    if key not in ids:
        ids[key] = len(ids) + 1
    return ids[key]


def type_name(t):
    """A struct's name as Raphael shows it: struct Node → Node, typedef struct {...} Point → Point."""
    s = t.strip_typedefs()
    if s.code in (gdb.TYPE_CODE_STRUCT, gdb.TYPE_CODE_UNION):
        if s.tag:
            return s.tag
        if t.code == gdb.TYPE_CODE_TYPEDEF and t.name:
            return t.name
        return "struct"
    if s.code == gdb.TYPE_CODE_ARRAY:
        return type_name(s.target()) + "[]"
    if s.code == gdb.TYPE_CODE_PTR:
        target = s.target().strip_typedefs()
        if target.code in (gdb.TYPE_CODE_STRUCT, gdb.TYPE_CODE_UNION):
            return type_name(s.target())  # a pointer to a Node is drawn as a link to a Node, as in Java
        return str(t)
    return str(t)


def is_char(t):
    s = t.strip_typedefs().unqualified()  # const char is a char too
    return s.code == gdb.TYPE_CODE_INT and s.name in ("char", "signed char", "unsigned char")


students_types = {}  # a struct's name → whether one of the student's files defines it


def students_type(t):
    """Whether the student wrote this struct (a FILE or other library struct shows only its name)."""
    name = type_name(t)
    if name not in students_types:
        tag = t.strip_typedefs().tag
        found = None
        try:
            if tag:
                found = gdb.lookup_symbol(tag, domain=gdb.SYMBOL_STRUCT_DOMAIN)[0]
            if found is None and t.code == gdb.TYPE_CODE_TYPEDEF and t.name:
                found = gdb.lookup_symbol(t.name)[0]
        except gdb.error:
            found = None
        students_types[name] = found is not None and student_file(found.symtab) is not None
    return students_types[name]


def char_literal(code):
    if code == 0:
        return "'\\0'"
    if code == 10:
        return "'\\n'"
    if 32 <= code < 127:
        return "'" + chr(code) + "'"
    return str(code)


class Step:
    """What one step reads: the objects it reached, by id, and where each variable is in memory."""

    def __init__(self):
        self.heap = {}
        self.places = []  # (start, size, name, element size or 0) for every variable in sight
        self.pointers = {}  # a block's start → the name of a variable pointing at it, for &a[3]

    def place(self, name, v):
        try:
            if v.address is None:
                return
            t = v.type.strip_typedefs()
            element = t.target().sizeof if t.code == gdb.TYPE_CODE_ARRAY else 0
            self.places.append((int(v.address), t.sizeof, name, element))
            if t.code == gdb.TYPE_CODE_PTR and not unset(v):
                target = int(v)
                if STEP_BLOCKS[0].starting_at(target) is not None:
                    self.pointers.setdefault(target, name)
        except (gdb.error, RuntimeError):
            pass

    def named_address(self, address, element_size=0):
        """A pointer as C would write where it points: &x, &arr[3], or &a[3] inside the block a
        points to; None if it's none of these."""
        for start, size, name, element in self.places:
            if address == start and not element:
                return "&" + name
        for start, size, name, element in self.places:
            if element and start <= address <= start + size and (address - start) % element == 0:  # one past the end too
                return "&" + name + "[" + str((address - start) // element) + "]"
        block = STEP_BLOCKS[0].at(address) if element_size > 0 else None
        if block is None and element_size > 0:
            block = STEP_BLOCKS[0].at(address - 1)  # one past the end of a block
            block = block if block is not None and address == block[0] + block[1] else None
        if block is not None and (address - block[0]) % element_size == 0:
            name = self.pointers.get(block[0])
            if name is not None:
                return "&" + name + "[" + str((address - block[0]) // element_size) + "]"
        return None

    def block_array(self, block, element):
        """A malloc'd block as an array of its elements: its size / the element's size."""
        start, size, freed = block
        name = type_name(element) + "[]"
        oid = object_id(start, name)
        if oid in self.heap:
            return {"r": oid}
        if len(self.heap) >= MAX_OBJECTS:
            return {"p": hex(start)}
        length = size // max(element.sizeof, 1)
        node = {"k": "a", "type": name, "elem": type_name(element), "items": [], "len": length}
        if freed:
            node["freed"] = True
        self.heap[oid] = node
        base = gdb.Value(start).cast(element.pointer())
        items = []
        for i in range(min(length, MAX_CELLS)):
            items.append(self.value((base + i).dereference()))
        node["items"] = items
        return {"r": oid}

    def value(self, v):
        t = v.type.strip_typedefs()
        try:
            if t.code in (gdb.TYPE_CODE_INT, gdb.TYPE_CODE_FLT, gdb.TYPE_CODE_PTR, gdb.TYPE_CODE_BOOL, gdb.TYPE_CODE_ENUM) and unset(v):
                return {"p": "?"}  # malloc'd, never written
            if t.code == gdb.TYPE_CODE_PTR:
                address = int(v)
                if address == 0:
                    return {"n": 1}
                target = t.target().strip_typedefs()
                if target.code in (gdb.TYPE_CODE_STRUCT, gdb.TYPE_CODE_UNION):
                    block = STEP_BLOCKS[0].starting_at(address)
                    if block is not None and target.sizeof > 0 and block[1] >= 2 * target.sizeof:
                        return self.block_array(block, t.target())  # malloc(n * sizeof(struct …)): an array of them
                    return self.struct_at(address, t.target())
                if is_char(target):
                    return {"p": json.dumps(v.string(errors="replace")[:200])}  # up to its '\0'
                if target.code == gdb.TYPE_CODE_FUNC:
                    return {"p": str(v).split(" ")[-1].strip("<>")}
                block = STEP_BLOCKS[0].starting_at(address)
                if block is not None and target.sizeof > 0 and target.code != gdb.TYPE_CODE_VOID:
                    return self.block_array(block, t.target())  # int *a = malloc(n * sizeof(int)): an array of n
                return {"p": self.named_address(address, target.sizeof) or hex(address)}
            if t.code == gdb.TYPE_CODE_ARRAY:
                return self.array(v)
            if t.code in (gdb.TYPE_CODE_STRUCT, gdb.TYPE_CODE_UNION):
                if v.address is None:
                    return {"p": str(v)}
                return self.struct_at(int(v.address), v.type)
            if t.code == gdb.TYPE_CODE_BOOL:
                return {"p": "true" if bool(v) else "false"}
            if t.code == gdb.TYPE_CODE_FLT:
                return {"p": repr(float(v))}
            if is_char(t):
                return {"p": char_literal(int(v))}
            if t.code == gdb.TYPE_CODE_INT:
                return {"p": str(int(v))}
            return {"p": str(v)}
        except (gdb.MemoryError, gdb.error, UnicodeDecodeError):
            return {"p": "?"}  # memory that can't be read: an unset or freed pointer

    def struct_at(self, address, t):
        name = type_name(t)
        if not students_type(t):  # FILE, not _IO_FILE: the name the student wrote
            return {"p": t.name if t.code == gdb.TYPE_CODE_TYPEDEF and t.name else name}
        oid = object_id(address, name)
        if oid in self.heap:
            return {"r": oid}
        if len(self.heap) >= MAX_OBJECTS:
            return {"p": hex(address)}
        try:
            v = gdb.Value(address).cast(t.pointer()).dereference()
            v.fetch_lazy()  # read it now, so a bad address fails here
        except (gdb.MemoryError, gdb.error):
            return {"p": "?"}
        node = {"k": "o", "type": name, "fields": {}, "ftypes": {}}
        block = STEP_BLOCKS[0].at(address)
        if block is not None and block[2]:
            node["freed"] = True  # free() gave it back: what's drawn is what it last held
        self.heap[oid] = node  # before the fields, so a cycle comes back to it
        for f in t.strip_typedefs().fields():
            if not f.name:
                continue
            ft = f.type.strip_typedefs()
            if ft.code == gdb.TYPE_CODE_ARRAY and is_char(ft.target()):
                # A char array inside a struct is a name or a word: shown as its text, up to the '\0'.
                node["fields"][f.name] = self.text(v[f.name])
                node["ftypes"][f.name] = "char[]"
                continue
            node["fields"][f.name] = self.value(v[f.name])
            node["ftypes"][f.name] = type_name(f.type)
        return {"r": oid}

    def text(self, v):
        if unset(v):
            return {"p": "?"}
        try:
            raw = bytes(int(v[i]) & 0xFF for i in range(v.type.strip_typedefs().sizeof))
            return {"p": json.dumps(raw.split(b"\0")[0].decode("utf-8", "replace"))}
        except (gdb.MemoryError, gdb.error):
            return {"p": "?"}

    def array(self, v):
        t = v.type.strip_typedefs()
        lo, hi = t.range()
        length = hi - lo + 1
        element = t.target()  # a char array stays an array of chars, so a string's cells can be watched one by one
        if v.address is None:
            return {"p": str(v)}
        name = type_name(v.type)
        oid = object_id(int(v.address), name)
        if oid in self.heap:
            return {"r": oid}
        node = {"k": "a", "type": name, "elem": type_name(element), "items": [], "len": length}
        self.heap[oid] = node
        node["items"] = [self.value(v[lo + i]) for i in range(min(length, MAX_CELLS))]
        return {"r": oid}


# ---- frames and locals ----

def locals_of(frame, line, step):
    names = {}
    for name, sym in visible_symbols(frame, line):
        try:
            names[name] = step.value(sym.value(frame))
        except (gdb.error, RuntimeError):
            pass
    return names


def visible_symbols(frame, line):
    """The variables in scope (name, symbol), outer blocks first, leaving out those whose
    declaration hasn't run yet."""
    blocks = []
    try:
        block = frame.block()
    except RuntimeError:
        return []
    while block is not None:
        blocks.append(block)
        if block.function is not None:
            break
        block = block.superblock
    found = {}
    for block in reversed(blocks):
        for sym in block:
            if not (sym.is_variable or sym.is_argument) or sym.addr_class == gdb.SYMBOL_LOC_TYPEDEF:
                continue
            if not sym.is_argument and sym.line >= line:
                continue  # declared on this line or later: not there yet, as in Java
            found[sym.name] = sym  # an inner block's variable hides an outer one's
    return list(found.items())


def frames_now(step):
    """The student's frames, the running one first; and the first one's frame object."""
    STEP_BLOCKS[0] = Blocks()
    frames = []
    f = gdb.newest_frame()
    while f is not None and student_file(f.find_sal().symtab) is None:
        f = f.older()  # a crash inside the C library: start at the student's code that called it
    top = f
    # First where every variable is, so a pointer to one in any frame can be named (&x).
    g, walked = f, 0
    while g is not None and walked < MAX_FRAMES and student_file(g.find_sal().symtab) is not None:
        walked += 1
        for name, sym in visible_symbols(g, g.find_sal().line):
            try:
                step.place(name, sym.value(g))
            except (gdb.error, RuntimeError):
                pass
        if g.name() == "main":
            break
        g = g.older()
    for name, sym in global_symbols():
        try:
            step.place(name, sym.value())
        except (gdb.error, RuntimeError):
            pass
    while f is not None and len(frames) < MAX_FRAMES:
        sal = f.find_sal()
        rel = student_file(sal.symtab)
        if rel is None:
            break
        name = f.name() or "?"
        frames.append({"fn": name, "file": rel, "line": sal.line, "locals": locals_of(f, sal.line, step)})
        if name == "main":
            break
        f = f.older()
    return frames, top


def global_symbols():
    """The global and file-level variables of the running file: (name, symbol)."""
    found = []
    seen = set()
    try:
        block = gdb.selected_frame().block()
        for static in (block.global_block, block.static_block):
            for sym in static:
                if sym.is_variable and sym.name not in seen and student_file(sym.symtab) is not None:
                    seen.add(sym.name)
                    found.append((sym.name, sym))
    except (gdb.error, RuntimeError, AttributeError):
        pass
    return found


def globals_now(step):
    result = {}
    for name, sym in global_symbols():
        try:
            result[name] = step.value(sym.value())
        except (gdb.error, RuntimeError):
            pass
    return result


def student_depth(frame):
    """How many of the student's calls are running, up to this frame."""
    depth = 0
    while frame is not None and depth <= MAX_FRAMES and student_file(frame.find_sal().symtab) is not None:
        depth += 1
        frame = frame.older()
    return depth


def returns_value(frame):
    try:
        return frame.function().type.target().code != gdb.TYPE_CODE_VOID
    except (AttributeError, RuntimeError, gdb.error):
        return False


# ---- the program's files ----

last_files = [None]


def files_now():
    """The run folder's files, if they changed since the last step (else None)."""
    if not RUN:
        return None
    try:
        entries = sorted((e for e in os.scandir(RUN) if e.is_file()), key=lambda e: e.name)[:MAX_FILES]
        signature = [(e.name, e.stat().st_size, e.stat().st_mtime_ns) for e in entries]
    except OSError:
        return None
    if signature == last_files[0]:
        return None
    last_files[0] = signature
    files = {}
    for e in entries:
        size = e.stat().st_size
        if size > MAX_FILE_BYTES:
            files[e.name] = "(%d bytes: too big to show)" % size
            continue
        try:
            with open(e.path, "rb") as f:
                data = f.read()
            files[e.name] = "(%d bytes: not text)" % size if b"\0" in data else data.decode("utf-8", "replace")
        except OSError as error:
            files[e.name] = "(couldn't be read: %s)" % error
    return files


def output_size():
    try:
        return os.path.getsize(STDOUT) if STDOUT else 0
    except OSError:
        return 0


# ---- returns ----

exits = []
armed = []  # the calls whose return is being watched (a Return each)


class Return(gdb.FinishBreakpoint):
    def __init__(self, frame, fn, rel):
        super().__init__(frame, internal=True)
        self.frame, self.fn, self.rel = frame, fn, rel

    def stop(self):
        value = self.return_value
        exits.append({"fn": self.fn, "file": self.rel, "returned": None if value is None else Step().value(value)})
        self.done()
        return False

    def out_of_scope(self):
        self.done()

    def done(self):
        # A later call can sit at the same address on the stack, and gdb would call it the same
        # frame, so a call that has returned stops being watched at once.
        if self in armed:
            armed.remove(self)


def watch_return(frame, fn, rel):
    if fn == "main":
        return
    for r in armed:
        try:
            if r.frame.is_valid() and r.frame == frame:
                return
        except gdb.error:
            pass
    try:
        armed.append(Return(frame, fn, rel))
    except (ValueError, gdb.error, RuntimeError):
        pass
    # gdb puts every watcher back into the program at each step, so deep recursion would slow down
    # step by step: only the newest calls are watched (as only the newest frames are read).
    while len(armed) > MAX_FRAMES:
        oldest = armed.pop(0)
        try:
            oldest.delete()
        except (gdb.error, RuntimeError):
            pass


# ---- signals and exit ----

state = {"signal": None, "exit": None}


def on_stop(event):
    if isinstance(event, gdb.SignalEvent):
        state["signal"] = event.stop_signal


def on_exit(event):
    state["exit"] = getattr(event, "exit_code", 0)


gdb.events.stop.connect(on_stop)
gdb.events.exited.connect(on_exit)


def finish(reason, **more):
    message = {"kind": "end", "reason": reason}
    message.update(more)
    send(message)
    if state["exit"] is None:
        try:
            gdb.execute("kill", to_string=True)
        except gdb.error:
            pass


def run():
    gdb.execute("break main", to_string=True)
    try:
        gdb.execute("run", to_string=True)
    except gdb.error as error:
        send({"kind": "end", "reason": "failed", "message": str(error)})
        return
    try:
        send({"kind": "start", "pid": gdb.selected_inferior().pid})
    except gdb.error:
        pass
    gdb.execute("delete", to_string=True)
    steps = 0
    last_text = {}  # call depth → the text of the line that call stopped on last
    while True:
        if state["exit"] is not None:
            finish("finished", code=state["exit"])
            return
        if STOP and os.path.exists(STOP):
            finish("stopped")
            return
        if state["signal"] is not None:
            step = Step()
            frames, _ = frames_now(step)
            finish("crash", signal=state["signal"], step={"frames": frames, "heap": step.heap,
                                                           "globals": globals_now(step), "exits": exits[:],
                                                           "out": output_size(), "files": files_now()})
            return
        try:
            frame = gdb.newest_frame()
            sal = frame.find_sal()
        except gdb.error:
            finish("finished", code=state["exit"] or 0)
            return
        rel = student_file(sal.symtab)
        command = "step"
        if rel is None:
            command = "finish"  # inside the C library (it has debug information on some systems): leave it
        elif frame.pc() == sal.pc:  # the start of a line, not the middle of one we came back to
            fn = frame.name() or "?"
            text = text_of(rel, sal.line)
            # After a return, C stops again on the function's closing brace; Java doesn't, so skip it:
            # always in a function that returns a value (only a return gets it there), and in a void
            # one when this call's last line was a return.
            depth = student_depth(frame)
            closing = text == "}" and (returns_value(frame) or last_text.get(depth, "").startswith("return"))
            # Some functions stop first on their own "int main(void) {" line; Java starts inside.
            try:
                opening = frame.function() is not None and frame.function().line == sal.line
            except RuntimeError:
                opening = False
            if not closing and not opening:
                watch_return(frame, fn, rel)
                step = Step()
                frames, _ = frames_now(step)
                message = {"kind": "step", "frames": frames, "heap": step.heap, "globals": globals_now(step),
                           "exits": exits[:], "out": output_size()}
                files = files_now()
                if files is not None:
                    message["files"] = files
                send(message)
                del exits[:]
                steps += 1
                if steps >= MAX_STEPS:
                    finish("steps")
                    return
            last_text[depth] = text
            for deeper in [d for d in last_text if d > depth]:
                del last_text[deeper]
        try:
            gdb.execute(command, to_string=True)
        except gdb.error:
            if command == "finish":
                try:
                    gdb.execute("step", to_string=True)
                except gdb.error:
                    pass
            if state["exit"] is None and state["signal"] is None:
                try:
                    gdb.newest_frame()
                except gdb.error:
                    finish("finished", code=0)
                    return


run()
out.flush()
