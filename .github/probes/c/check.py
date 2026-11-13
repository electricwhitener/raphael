# Checks a spike run: python3 check.py steps.jsonl
# Passes if the debugger (gdb, or lldb on macOS) recorded the list's steps and the recursive sum's return values in order.
import json, sys
lines = [json.loads(l) for l in open(sys.argv[1])]
summary, steps = lines[-1], lines[:-1]
# A return value is a string in the spike's format, or {"p": "10"} in Raphael's helpers' format.
plain = lambda v: v.get("p") if isinstance(v, dict) else v
returns = [plain(e.get("returned")) for s in steps for e in s["exits"] if e["fn"] == "sum"]
# A node whose fields can't be read came from a garbage pointer (malloc'd memory not set yet:
# Windows fills it with 0xBAADF00D), so it isn't counted.
readable = lambda node: "fields" in node and not all("bad" in f or f.get("p") == "?" for f in node["fields"].values())
heap_sizes = [sum(map(readable, s["heap"].values())) for s in steps]
print("steps:", summary["steps"], "ms per step: %.2f" % summary["ms_per_step"])
print("sum returned:", returns, "most nodes seen in one step:", max(heap_sizes))
ok = summary["steps"] >= 30 and returns == ["0", "10", "30", "60", "100"] and max(heap_sizes) == 4
print("PASS" if ok else "FAIL")
sys.exit(0 if ok else 1)
