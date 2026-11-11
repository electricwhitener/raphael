# Raphael's C tracer for macOS, run inside lldb (Apple's debugger; macOS has no gdb):
#   RAPHAEL_EXE=program lldb --batch --no-lldbinit -o "command script import raphael_lldb.py"
# It does what raphael_gdb.py does, with lldb's Python API, and prints the same JSON lines
# (see raphael_gdb.py for the settings and the lines), so CTracer reads either one.
# lldb has no FinishBreakpoint: a call's return is caught by a breakpoint at its return address,
# where the value is read from the register a function returns it in.
import bisect
import json
import os
import struct

import lldb

EXE = os.environ.get("RAPHAEL_EXE", "")
SRC = os.path.realpath(os.environ.get("RAPHAEL_SRC", "."))
RUN = os.environ.get("RAPHAEL_RUN")
STDOUT = os.environ.get("RAPHAEL_STDOUT")
STOP = os.environ.get("RAPHAEL_STOP", "")
MAX_STEPS = int(os.environ.get("RAPHAEL_MAX_STEPS", "2000"))
MAX_FRAMES = int(os.environ.get("RAPHAEL_MAX_FRAMES", "32"))
MAX_OBJECTS = int(os.environ.get("RAPHAEL_MAX_OBJECTS", "300"))
MAX_CELLS = int(os.environ.get("RAPHAEL_MAX_CELLS", "64"))
MAX_FILES, MAX_FILE_BYTES = 20, 20000
UNSET = 0xBE

out = os.fdopen(os.dup(1), "w", buffering=1, encoding="utf-8")


def send(message):
    out.write("\n@@R " + json.dumps(message) + "\n")


class Run:
    target = None
    process = None


def err():
    return lldb.SBError()


# ---- the student's files ----

lines_of = {}


def student_file(spec):
    """The program's own file a file spec names ("main.c"), or None."""
    if spec is None or not spec.IsValid() or not spec.fullpath:
        return None
    full = os.path.realpath(spec.fullpath)
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


def frame_file(frame):
    return student_file(frame.GetLineEntry().GetFileSpec())


def text_of(rel, line):
    lines = lines_of.get(rel) or []
    return lines[line - 1].strip() if 0 < line <= len(lines) else ""


# ---- the shim's table of malloc'd blocks ----

def read(address, size):
    e = err()
    data = Run.process.ReadMemory(address, size, e)
    if not e.Success() or data is None:
        raise MemoryError()
    return bytes(data)


def symbol_address(name):
    for ctx in Run.target.FindSymbols(name):
        address = ctx.GetSymbol().GetStartAddress().GetLoadAddress(Run.target)
        if address != lldb.LLDB_INVALID_ADDRESS:
            return address
    return None


class Blocks:
    def __init__(self, load=True):
        self.starts, self.blocks = [], []
        if not load:
            return
        count_at, table = symbol_address("raphael_block_count"), symbol_address("raphael_blocks")
        if count_at is None or table is None:
            return
        try:
            count = struct.unpack("<i", read(count_at, 4))[0]
            if count <= 0:
                return
            rows = sorted((a, n, f) for a, n, f in struct.iter_unpack("<QQi4x", read(table, count * 24)) if a)
        except MemoryError:
            return
        self.blocks = rows
        self.starts = [r[0] for r in rows]

    def at(self, address):
        i = bisect.bisect_right(self.starts, address) - 1
        while i >= 0:
            start, size, freed = self.blocks[i]
            if start <= address < start + max(size, 1):
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


STEP_BLOCKS = [Blocks(load=False)]


def unset(v):
    address = v.GetLoadAddress()
    if address == lldb.LLDB_INVALID_ADDRESS or STEP_BLOCKS[0].at(address) is None:
        return False
    size = v.GetType().GetByteSize()
    try:
        data = read(address, size)
    except MemoryError:
        return False
    return size > 0 and all(b == UNSET for b in data)


# ---- types ----

STRUCTS = (lldb.eTypeClassStruct, lldb.eTypeClassUnion, lldb.eTypeClassClass)
CHARS = (lldb.eBasicTypeChar, lldb.eBasicTypeSignedChar, lldb.eBasicTypeUnsignedChar)
FLOATS = (lldb.eBasicTypeFloat, lldb.eBasicTypeDouble, lldb.eBasicTypeLongDouble)
UNSIGNED = (lldb.eBasicTypeUnsignedChar, lldb.eBasicTypeUnsignedShort, lldb.eBasicTypeUnsignedInt,
            lldb.eBasicTypeUnsignedLong, lldb.eBasicTypeUnsignedLongLong)


def canonical(t):
    return t.GetCanonicalType().GetUnqualifiedType()


def type_name(t):
    """struct Node → Node; typedef struct {...} Point → Point; int[] for arrays."""
    c = canonical(t)
    tc = c.GetTypeClass()
    if tc in STRUCTS:
        name = c.GetName() or ""
        for prefix in ("struct ", "union ", "const ", "volatile "):
            name = name.replace(prefix, "")
        if not name or name.startswith("(") or "unnamed" in name or "anonymous" in name:
            plain = t.GetUnqualifiedType()
            if plain.IsTypedefType():
                return plain.GetName()
            return "struct"
        return name
    if tc == lldb.eTypeClassArray:
        return type_name(c.GetArrayElementType()) + "[]"
    if tc == lldb.eTypeClassPointer:
        pointee = t.GetUnqualifiedType().GetPointeeType()
        if not pointee.IsValid():
            pointee = c.GetPointeeType()
        if canonical(pointee).GetTypeClass() in STRUCTS:
            return type_name(pointee)
        return t.GetName()
    return t.GetName()


def is_char(t):
    c = canonical(t)
    return c.GetTypeClass() == lldb.eTypeClassBuiltin and c.GetBasicType() in CHARS


def char_literal(code):
    code &= 0xFF
    if code == 0:
        return "'\\0'"
    if code == 10:
        return "'\\n'"
    if 32 <= code < 127:
        return "'" + chr(code) + "'"
    return str(code)


def library_struct(t):
    """A struct the C library defines (FILE is struct __sFILE on a Mac): shown by its name only."""
    name = canonical(t).GetName() or ""
    return name.replace("struct ", "").startswith("_")


ids = {}


def object_id(address, name):
    key = (address, name)
    if key not in ids:
        ids[key] = len(ids) + 1
    return ids[key]


def value_at(address, t, name="v"):
    return Run.target.CreateValueFromAddress(name, lldb.SBAddress(address, Run.target), t)


class Step:
    def __init__(self):
        self.heap = {}
        self.places = []
        self.pointers = {}

    def place(self, name, v):
        address = v.GetLoadAddress()
        if address == lldb.LLDB_INVALID_ADDRESS:
            return
        c = canonical(v.GetType())
        element = c.GetArrayElementType().GetByteSize() if c.GetTypeClass() == lldb.eTypeClassArray else 0
        self.places.append((address, c.GetByteSize(), name, element))
        if c.GetTypeClass() == lldb.eTypeClassPointer and not unset(v):
            target = v.GetValueAsUnsigned()
            if STEP_BLOCKS[0].starting_at(target) is not None:
                self.pointers.setdefault(target, name)

    def named_address(self, address, element_size=0):
        for start, size, name, element in self.places:
            if address == start and not element:
                return "&" + name
        for start, size, name, element in self.places:
            if element and start <= address <= start + size and (address - start) % element == 0:
                return "&" + name + "[" + str((address - start) // element) + "]"
        block = STEP_BLOCKS[0].at(address) if element_size > 0 else None
        if block is None and element_size > 0:
            block = STEP_BLOCKS[0].at(address - 1)
            block = block if block is not None and address == block[0] + block[1] else None
        if block is not None and (address - block[0]) % element_size == 0:
            name = self.pointers.get(block[0])
            if name is not None:
                return "&" + name + "[" + str((address - block[0]) // element_size) + "]"
        return None

    def value(self, v):
        if not v.IsValid():
            return {"p": "?"}
        t = v.GetType()
        c = canonical(t)
        tc = c.GetTypeClass()
        if tc in (lldb.eTypeClassBuiltin, lldb.eTypeClassPointer, lldb.eTypeClassEnumeration) and unset(v):
            return {"p": "?"}
        if tc == lldb.eTypeClassPointer:
            address = v.GetValueAsUnsigned()
            if address == 0:
                return {"n": 1}
            pointee = t.GetUnqualifiedType().GetPointeeType()  # FILE, not __sFILE: the type as written
            if not pointee.IsValid():
                pointee = c.GetPointeeType()
            pc = canonical(pointee)
            if pc.GetTypeClass() in STRUCTS:
                block = STEP_BLOCKS[0].starting_at(address)
                if block is not None and pc.GetByteSize() > 0 and block[1] >= 2 * pc.GetByteSize():
                    return self.block_array(block, pointee)
                return self.struct_at(address, pointee)
            if is_char(pointee):
                e = err()
                text = Run.process.ReadCStringFromMemory(address, 200, e)
                return {"p": json.dumps(text) if e.Success() else "?"}
            if pc.GetTypeClass() == lldb.eTypeClassFunction:
                return {"p": Run.target.ResolveLoadAddress(address).GetFunction().GetName() or hex(address)}
            block = STEP_BLOCKS[0].starting_at(address)
            if block is not None and pc.GetByteSize() > 0 and pc.GetBasicType() != lldb.eBasicTypeVoid:
                return self.block_array(block, pointee)
            return {"p": self.named_address(address, pc.GetByteSize()) or hex(address)}
        if tc == lldb.eTypeClassArray:
            return self.array(v)
        if tc in STRUCTS:
            address = v.GetLoadAddress()
            if address == lldb.LLDB_INVALID_ADDRESS:
                return {"p": v.GetSummary() or "struct"}
            return self.struct_at(address, t)
        if tc == lldb.eTypeClassEnumeration:
            return {"p": v.GetValue() or str(v.GetValueAsSigned())}
        basic = c.GetBasicType()
        if basic == lldb.eBasicTypeBool:
            return {"p": "true" if v.GetValueAsUnsigned() else "false"}
        if basic in CHARS:
            return {"p": char_literal(v.GetValueAsUnsigned())}
        if basic in FLOATS:
            try:
                return {"p": repr(float(v.GetValue()))}
            except (TypeError, ValueError):
                return {"p": v.GetValue() or "?"}
        if basic in UNSIGNED:
            return {"p": str(v.GetValueAsUnsigned())}
        return {"p": str(v.GetValueAsSigned())}

    def struct_at(self, address, t):
        name = type_name(t)
        if library_struct(t):
            plain = t.GetUnqualifiedType()
            return {"p": plain.GetName() if plain.IsTypedefType() else name}
        oid = object_id(address, name)
        if oid in self.heap:
            return {"r": oid}
        if len(self.heap) >= MAX_OBJECTS:
            return {"p": hex(address)}
        try:
            read(address, max(1, canonical(t).GetByteSize()))
        except MemoryError:
            return {"p": "?"}
        v = value_at(address, t)
        node = {"k": "o", "type": name, "fields": {}, "ftypes": {}}
        block = STEP_BLOCKS[0].at(address)
        if block is not None and block[2]:
            node["freed"] = True
        self.heap[oid] = node
        c = canonical(t)
        for i in range(c.GetNumberOfFields()):
            field = c.GetFieldAtIndex(i)
            fname = field.GetName()
            if not fname:
                continue
            child = v.GetChildMemberWithName(fname)
            ft = field.GetType()
            if canonical(ft).GetTypeClass() == lldb.eTypeClassArray and is_char(canonical(ft).GetArrayElementType()):
                node["fields"][fname] = self.text(child)
                node["ftypes"][fname] = "char[]"
                continue
            node["fields"][fname] = self.value(child)
            node["ftypes"][fname] = type_name(ft)
        return {"r": oid}

    def text(self, v):
        if unset(v):
            return {"p": "?"}
        try:
            raw = read(v.GetLoadAddress(), v.GetType().GetByteSize())
        except MemoryError:
            return {"p": "?"}
        return {"p": json.dumps(raw.split(b"\0")[0].decode("utf-8", "replace"))}

    def array(self, v):
        t = v.GetType()
        c = canonical(t)
        element = c.GetArrayElementType()
        length = c.GetByteSize() // max(1, element.GetByteSize())
        address = v.GetLoadAddress()
        if address == lldb.LLDB_INVALID_ADDRESS:
            return {"p": v.GetSummary() or "array"}
        name = type_name(t)
        oid = object_id(address, name)
        if oid in self.heap:
            return {"r": oid}
        node = {"k": "a", "type": name, "elem": type_name(element), "items": [], "len": length}
        self.heap[oid] = node
        node["items"] = [self.value(v.GetChildAtIndex(i)) for i in range(min(length, MAX_CELLS))]
        return {"r": oid}

    def block_array(self, block, element):
        start, size, freed = block
        name = type_name(element) + "[]"
        oid = object_id(start, name)
        if oid in self.heap:
            return {"r": oid}
        if len(self.heap) >= MAX_OBJECTS:
            return {"p": hex(start)}
        width = max(1, canonical(element).GetByteSize())
        length = size // width
        node = {"k": "a", "type": name, "elem": type_name(element), "items": [], "len": length}
        if freed:
            node["freed"] = True
        self.heap[oid] = node
        node["items"] = [self.value(value_at(start + i * width, element)) for i in range(min(length, MAX_CELLS))]
        return {"r": oid}


# ---- frames and locals ----

def visible(frame, line):
    found = {}
    variables = frame.GetVariables(True, True, False, True)  # arguments, locals, no statics, in scope
    for i in range(variables.GetSize()):
        v = variables.GetValueAtIndex(i)
        name = v.GetName()
        if not name:
            continue
        if v.GetValueType() != lldb.eValueTypeVariableArgument:
            declared = v.GetDeclaration()
            if declared.IsValid() and declared.GetLine() >= line:
                continue  # declared on this line or later: not there yet, as in Java
        found[name] = v
    return list(found.items())


def global_values():
    found = []
    frame = Run.process.GetSelectedThread().GetFrameAtIndex(0)
    variables = frame.GetVariables(False, False, True, True)
    for i in range(variables.GetSize()):
        v = variables.GetValueAtIndex(i)
        declared = v.GetDeclaration()
        if v.GetName() and declared.IsValid() and student_file(declared.GetFileSpec()) is not None:
            found.append((v.GetName(), v))
    return found


def frames_now(step, thread):
    STEP_BLOCKS[0] = Blocks()
    frames = []
    index = 0
    count = thread.GetNumFrames()
    while index < count and frame_file(thread.GetFrameAtIndex(index)) is None:
        index += 1  # a crash inside the C library: start at the student's code that called it
    first = index
    walked = 0
    k = first
    while k < count and walked < MAX_FRAMES and frame_file(thread.GetFrameAtIndex(k)) is not None:
        f = thread.GetFrameAtIndex(k)
        for name, v in visible(f, f.GetLineEntry().GetLine()):
            step.place(name, v)
        walked += 1
        if f.GetFunctionName() == "main":
            break
        k += 1
    for name, v in global_values():
        step.place(name, v)
    k = first
    while k < count and len(frames) < MAX_FRAMES:
        f = thread.GetFrameAtIndex(k)
        rel = frame_file(f)
        if rel is None:
            break
        name = f.GetFunctionName() or "?"
        line = f.GetLineEntry().GetLine()
        frames.append({"fn": name, "file": rel, "line": line,
                       "locals": {n: step.value(v) for n, v in visible(f, line)}})
        if name == "main":
            break
        k += 1
    return frames


def globals_now(step):
    return {name: step.value(v) for name, v in global_values()}


def student_depth(thread):
    depth = 0
    for k in range(min(thread.GetNumFrames(), MAX_FRAMES + 1)):
        if frame_file(thread.GetFrameAtIndex(k)) is None:
            break
        depth += 1
    return depth


def student_depth_anywhere(thread):
    """Whether any of the student's functions is on the stack."""
    return any(frame_file(thread.GetFrameAtIndex(k)) is not None for k in range(thread.GetNumFrames()))


def returns_value(frame):
    rtype = frame.GetFunction().GetType().GetFunctionReturnType()
    return rtype.IsValid() and canonical(rtype).GetBasicType() != lldb.eBasicTypeVoid


# ---- the program's files ----

last_files = [None]


def files_now():
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


# ---- returns: a breakpoint at each call's return address ----

exits = []
calls = []          # the calls being watched: {fn, file, cfa, caller_cfa, ret, type}
return_points = {}  # return address → its breakpoint


def arm64():
    return Run.target.GetTriple().startswith(("arm64", "aarch64"))


def watch_return(thread):
    frame = thread.GetFrameAtIndex(0)
    fn = frame.GetFunctionName()
    if fn == "main" or thread.GetNumFrames() < 2:
        return
    cfa = frame.GetCFA()
    for call in calls:
        if call["cfa"] == cfa and call["fn"] == fn:
            return
    caller = thread.GetFrameAtIndex(1)
    ret = caller.GetPC()
    if ret not in return_points:
        return_points[ret] = Run.target.BreakpointCreateByAddress(ret)
    calls.append({"fn": fn, "file": frame_file(frame), "cfa": cfa, "caller_cfa": caller.GetCFA(), "ret": ret,
                  "type": frame.GetFunction().GetType().GetFunctionReturnType()})
    del calls[:-MAX_FRAMES]  # only the newest calls are watched


def returned(thread):
    """At a return address: the watched call that just returned, with its value from the register."""
    frame = thread.GetFrameAtIndex(0)
    pc, cfa = frame.GetPC(), frame.GetCFA()
    for i in range(len(calls) - 1, -1, -1):
        call = calls[i]
        if call["ret"] == pc and call["caller_cfa"] == cfa:
            del calls[i:]
            exits.append({"fn": call["fn"], "file": call["file"], "returned": return_value(frame, call["type"])})
            return True
    return False


def return_value(frame, rtype):
    if not rtype.IsValid():
        return None
    c = canonical(rtype)
    if c.GetBasicType() == lldb.eBasicTypeVoid:
        return None
    if c.GetTypeClass() in STRUCTS or c.GetByteSize() > 8:
        return None  # a struct comes back in several registers or through memory: not read
    if c.GetTypeClass() == lldb.eTypeClassBuiltin and c.GetBasicType() in FLOATS:
        register = frame.FindRegister("d0" if arm64() else "xmm0")
        data = register.GetData()
        e = err()
        number = data.GetFloat(e, 0) if c.GetByteSize() == 4 else data.GetDouble(e, 0)
        return {"p": repr(float(number))} if e.Success() else None
    raw = frame.FindRegister("x0" if arm64() else "rax").GetValueAsUnsigned()
    data = lldb.SBData.CreateDataFromUInt64Array(Run.target.GetByteOrder(), Run.target.GetAddressByteSize(), [raw])
    value = Run.target.CreateValueFromData("returned", data, rtype)
    return Step().value(value)


# ---- the run ----

def crash_signal(thread):
    reason = thread.GetStopReason()
    if reason == lldb.eStopReasonSignal:
        number = thread.GetStopReasonDataAtIndex(0)
        name = Run.process.GetUnixSignals().GetSignalAsCString(number)
        return name or ("signal %d" % number)
    if reason == lldb.eStopReasonException:
        description = thread.GetStopDescription(200) or ""
        for exception, signal in (("EXC_BAD_ACCESS", "SIGSEGV"), ("EXC_ARITHMETIC", "SIGFPE"),
                                  ("EXC_BAD_INSTRUCTION", "SIGILL"), ("EXC_BREAKPOINT", "SIGTRAP")):
            if exception in description:
                return signal
        return "?"
    return None


landing = [None]  # a skipped step (back on a line after its calls returned), kept for returns with nowhere else to go


def finish_returns():
    """Returns that happened in main's last line, with no step after them to land on: they land
    on main's stop after them, which was skipped as a doubled step."""
    if landing[0] is not None and exits:
        step = dict(landing[0])
        step["exits"] = exits[:]
        step["out"] = output_size()
        send(step)
        del exits[:]


def finish(reason, **more):
    if reason == "finished":
        finish_returns()
    message = {"kind": "end", "reason": reason}
    message.update(more)
    send(message)
    if Run.process is not None and Run.process.GetState() != lldb.eStateExited:
        Run.process.Kill()


def step_message(thread):
    step = Step()
    frames = frames_now(step, thread)
    message = {"kind": "step", "frames": frames, "heap": step.heap, "globals": globals_now(step),
               "exits": exits[:], "out": output_size()}
    files = files_now()
    if files is not None:
        message["files"] = files
    return message


def run(debugger):
    debugger.SetAsync(False)
    Run.target = debugger.CreateTarget(EXE)
    if not Run.target.IsValid():
        send({"kind": "end", "reason": "failed", "message": "lldb couldn't open " + EXE})
        return
    main = Run.target.BreakpointCreateByName("main")
    info = lldb.SBLaunchInfo(None)
    info.SetWorkingDirectory(os.getcwd())
    info.SetEnvironmentEntries(["%s=%s" % kv for kv in os.environ.items()], False)
    e = err()
    Run.process = Run.target.Launch(info, e)
    if not e.Success() or Run.process is None or not Run.process.IsValid():
        send({"kind": "end", "reason": "failed", "message": e.GetCString() or "the program didn't start"})
        return
    Run.target.BreakpointDelete(main.GetID())
    send({"kind": "start", "pid": Run.process.GetProcessID()})
    steps = 0
    last_text = {}
    last_line_of = {}  # a frame (its CFA) → the (file, line) of its last step
    while True:
        state = Run.process.GetState()
        if state in (lldb.eStateExited, lldb.eStateDetached, lldb.eStateInvalid):
            finish("finished", code=Run.process.GetExitStatus())
            return
        if STOP and os.path.exists(STOP):
            finish("stopped")
            return
        thread = Run.process.GetSelectedThread()
        signal = crash_signal(thread)
        if signal is not None and signal != "SIGTRAP":
            step = Step()
            frames = frames_now(step, thread)
            finish("crash", signal=signal, step={"frames": frames, "heap": step.heap, "globals": globals_now(step),
                                                  "exits": exits[:], "out": output_size(), "files": files_now()})
            return
        frame = thread.GetFrameAtIndex(0)
        if frame.GetPC() in return_points:
            returned(thread)  # whether lldb calls it a breakpoint hit or the end of a step
        entry = frame.GetLineEntry()
        rel = student_file(entry.GetFileSpec())
        if rel is None:
            if student_depth_anywhere(thread):
                thread.StepOut()  # inside the C library: leave it
            else:
                if exits:
                    finish_returns()
                Run.process.Continue()  # main has returned: let the program end
            continue
        at_start = frame.GetPC() == entry.GetStartAddress().GetLoadAddress(Run.target)
        if at_start:
            fn = frame.GetFunctionName() or "?"
            line = entry.GetLine()
            text = text_of(rel, line)
            depth = student_depth(thread)
            closing = text == "}" and (returns_value(frame) or last_text.get(depth, "").startswith("return"))
            if closing and fn == "main" and exits:
                closing = False
            start_line = frame.GetFunction().GetStartAddress().GetLineEntry().GetLine()
            opening = line == start_line and text.endswith("{")
            # Apple's compiler starts a new line-table row where a call returns, so lldb can stop
            # twice on one line; a frame's step on the line it last stopped on is skipped.
            cfa = frame.GetCFA()
            again = last_line_of.get(cfa) == (rel, line)
            if again and exits:
                landing[0] = step_message(thread)
            if not closing and not opening and not again:
                last_line_of[cfa] = (rel, line)
                for deeper in [c for c in last_line_of if c < cfa]:  # the stack grows down: those calls are over
                    del last_line_of[deeper]
                watch_return(thread)
                send(step_message(thread))
                landing[0] = None
                del exits[:]
                steps += 1
                if steps >= MAX_STEPS:
                    finish("steps")
                    return
            last_text[depth] = text
            for deeper in [d for d in last_text if d > depth]:
                del last_text[deeper]
        thread.StepInto()


def __lldb_init_module(debugger, internal_dict):
    try:
        run(debugger)
    except Exception as error:  # a bug here must still end the run with a message
        send({"kind": "end", "reason": "failed", "message": "%s: %s" % (type(error).__name__, error)})
    out.flush()
