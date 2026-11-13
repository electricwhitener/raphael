# Stage 15 probe: what lldb reports at C++'s exception breakpoints (run by .github/workflows/macos-probe.yml).
import lldb, os


def __lldb_init_module(debugger, d):
    debugger.SetAsync(False)
    target = debugger.CreateTarget(os.environ["EXE"])
    main = target.BreakpointCreateByName("main")
    process = target.LaunchSimple(None, None, os.getcwd())
    target.BreakpointDelete(main.GetID())
    throw_bp = target.BreakpointCreateForException(lldb.eLanguageTypeC_plus_plus, False, True)
    catch_bp = target.BreakpointCreateForException(lldb.eLanguageTypeC_plus_plus, True, False)
    print("PROBE bps: throw", throw_bp.GetID(), throw_bp.GetNumLocations(), "catch", catch_bp.GetID(), catch_bp.GetNumLocations())
    for i in range(80):
        if process.GetState() != lldb.eStateStopped:
            print("PROBE state", process.GetState())
            break
        thread = process.GetSelectedThread()
        frame = thread.GetFrameAtIndex(0)
        entry = frame.GetLineEntry()
        reason = thread.GetStopReason()
        data = [thread.GetStopReasonDataAtIndex(k) for k in range(thread.GetStopReasonDataCount())]
        print("PROBE stop", i, "reason", reason, "data", data, "fn", frame.GetFunctionName(), "line", entry.GetLine(),
              "file", entry.GetFileSpec().GetFilename(), "desc", thread.GetStopDescription(100))
        if reason == lldb.eStopReasonBreakpoint and data and data[0] == throw_bp.GetID():
            regs = {n: frame.FindRegister(n).GetValueAsUnsigned() for n in ("x0", "x1", "rdi", "rsi")}
            print("PROBE throw regs", {k: hex(v) for k, v in regs.items()}, "triple", target.GetTriple())
            e = lldb.SBError()
            ti = regs["x1"] if target.GetTriple().startswith(("arm64", "aarch64")) else regs["rsi"]
            name_ptr = process.ReadPointerFromMemory(ti + 8, e)
            print("PROBE name ptr", hex(name_ptr), e.Success(), "masked", process.ReadCStringFromMemory(name_ptr & 0x0000FFFFFFFFFFFF, 100, e), e.Success())
        if entry.GetFileSpec().GetFilename() == "throw.cpp":
            thread.StepInto()
        else:
            thread.StepOut()
