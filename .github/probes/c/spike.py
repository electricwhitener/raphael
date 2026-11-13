import gdb, json, time, os
out = open(os.environ.get("RAPHAEL_OUT", "/dev/stderr"), "w")
STUDENT = {"list.c"}
gdb.execute("set pagination off"); gdb.execute("set confirm off")
for setting in ("startup-with-shell off", "debuginfod enabled off"):  # not every gdb has these (Windows has neither)
    try:
        gdb.execute("set " + setting)
    except gdb.error:
        pass
gdb.execute("tbreak main", to_string=True); gdb.execute("run", to_string=True)
EXITS = []
class Ret(gdb.FinishBreakpoint):
    def stop(self):
        rv = self.return_value
        EXITS.append({"fn": self.fn, "returned": None if rv is None else str(rv)})
        return False
    def out_of_scope(self):
        EXITS.append({"fn": self.fn, "left": True})
seen_depth = [0]

def plain(v):
    t = v.type.strip_typedefs()
    try:
        if t.code == gdb.TYPE_CODE_PTR:
            a = int(v)
            return {"ref": a} if a else {"null": True}
        if t.code == gdb.TYPE_CODE_ARRAY:
            lo, hi = t.range()
            return {"array": [plain(v[i]) for i in range(lo, min(hi, 63) + 1)]}
        if t.code == gdb.TYPE_CODE_STRUCT:
            return {"struct": {f.name: plain(v[f.name]) for f in t.fields()}}
        return {"v": str(v)}
    except gdb.MemoryError:
        return {"bad": True}

def walk(heap, addr, typ, depth=0):
    if addr in heap or depth > 200: return
    try:
        v = gdb.Value(addr).cast(typ.pointer()).dereference()
        s = plain(v)
    except gdb.MemoryError:
        return
    heap[addr] = {"type": str(typ.strip_typedefs()).replace("struct ", ""), "fields": s["struct"]}
    for f in typ.strip_typedefs().fields():
        ft = f.type.strip_typedefs()
        if ft.code == gdb.TYPE_CODE_PTR and ft.target().strip_typedefs().code == gdb.TYPE_CODE_STRUCT:
            a = int(v[f.name])
            if a: walk(heap, a, ft.target(), depth + 1)

steps = 0; t0 = time.time()
while steps < 2000:
    try:
        fr = gdb.newest_frame()
    except gdb.error:
        break
    sal = fr.find_sal()
    if sal.symtab is None or os.path.basename(sal.symtab.filename) not in STUDENT:
        gdb.execute("finish", to_string=True); continue
    frames = []; heap = {}
    f = fr
    while f is not None and len(frames) < 32:
        s = f.find_sal()
        if s.symtab is None: break
        loc = {}
        try:
            b = f.block()
            while b is not None:          # the loop's block, then the blocks around it, up to the function's
                for sym in b:
                    if (sym.is_variable or sym.is_argument) and sym.name not in loc:
                        v = sym.value(f); loc[sym.name] = plain(v)
                        t = v.type.strip_typedefs()
                        if t.code == gdb.TYPE_CODE_PTR and t.target().strip_typedefs().code == gdb.TYPE_CODE_STRUCT and int(v):
                            walk(heap, int(v), t.target())
                if b.function is not None: break
                b = b.superblock
        except RuntimeError: pass
        frames.append({"fn": f.name(), "line": s.line, "locals": loc})
        f = f.older()
    if len(frames) > seen_depth[0] and len(frames) > 1:   # a new call: catch its return
        try:
            r = Ret(fr, internal=True); r.fn = fr.name()
        except (ValueError, gdb.error): pass
    seen_depth[0] = len(frames)
    out.write(json.dumps({"frames": frames, "exits": EXITS, "heap": {str(k): v for k, v in heap.items()}}) + "\n")
    EXITS.clear()
    steps += 1
    try:
        gdb.execute("step", to_string=True)
    except gdb.error:
        break
out.write(json.dumps({"steps": steps, "ms_per_step": (time.time() - t0) * 1000 / max(1, steps)}) + "\n")
out.flush()

