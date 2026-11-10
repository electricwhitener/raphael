package raphael.explain;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import raphael.analysis.CodeFacts;
import raphael.model.Exit;
import raphael.model.FrameSnapshot;
import raphael.model.Snapshot;
import raphael.model.Trace;

// Recursion: a method calling itself. The moments: the first time it calls itself, each base
// case (a return that doesn't call it again), and the unwinding, with every return's value and
// how the caller worked out its own answer from it ("factorial(n = 3) returns 3 * 2 = 6").
// The returns come from the run's exits (see model/Exit), so they're never lost inside one line.
// OOP: inheritance (a Concept); it overrides relevant() and find().
class RecursionConcept extends Concept {

    @Override
    protected boolean relevant(CodeFacts facts) {
        return facts.hasRecursion();
    }

    @Override
    protected void find(Trace trace, CodeFacts facts, List<Moment> into) {
        Set<String> started = new HashSet<>();
        boolean firstReturns = true;
        int baseCases = 0; // only the first two base cases of a run pause; the rest are listed
        for (int s = 1; s < trace.size(); s++) {
            Snapshot before = trace.get(s - 1);
            Snapshot now = trace.get(s);
            if (now.getFrames().isEmpty()) {
                continue;
            }
            FrameSnapshot top = now.getFrames().get(0);
            CodeFacts.MethodFact method = facts.methodAt(top.getFile(), top.getLine());
            if (method != null && method.recursive() && method.name().equals(top.getMethodName())) {
                firstSelfCall(s, now, top, method, started, into);
                if (baseCase(trace, s, now, top, method, facts, into, baseCases < 2)) {
                    baseCases++;
                }
            }
            String unwinding = returns(before, now, facts);
            if (unwinding != null) {
                into.add(new Moment(s, "returns", "Returning", unwinding, firstReturns, top.getFile(), top.getLine()));
                firstReturns = false;
            }
        }
        noBaseCase(trace, into);
    }

    // A run stopped at the step limit by a method calling itself again and again: its base case
    // is missing (or never reached).
    private void noBaseCase(Trace trace, List<Moment> into) {
        if (trace.getEndReason() != raphael.model.EndReason.STEP_LIMIT || !trace.getEndMessage().contains("calling itself")) {
            return;
        }
        int last = trace.size() - 1;
        Snapshot end = trace.get(last);
        FrameSnapshot top = end.getFrames().isEmpty() ? null : end.getFrames().get(0);
        String method = top == null ? "the method" : top.getMethodName() + "()";
        into.add(new Moment(last, "no-base-case", "No base case reached",
                method + " called itself " + (end.getFrames().size() - 1) + "+ times and never stopped: no call reached a base case "
                        + "(a return that doesn't call itself again). Look for the if that should stop it, and whether each call gets closer to it.",
                true, top == null ? null : top.getFile(), end.getLine()));
    }

    // The first time a method is on the stack twice: it has called itself.
    private void firstSelfCall(int step, Snapshot now, FrameSnapshot top, CodeFacts.MethodFact method,
                               Set<String> started, List<Moment> into) {
        if (started.contains(method.name()) || now.getFrames().size() < 2) {
            return;
        }
        FrameSnapshot caller = now.getFrames().get(1);
        if (!caller.getMethodName().equals(method.name())) {
            return;
        }
        started.add(method.name());
        into.add(new Moment(step, "calls-itself", method.name() + " calls itself",
                Phrases.call(top, method.parameters(), now) + " starts, while " + Phrases.call(caller, method.parameters(), now)
                        + (method.returnsValue() ? " waits for its answer." : " waits for it to finish.")
                        + (method.parameters().isEmpty() ? "" : " Each call has its own " + Phrases.list(method.parameters()) + "."),
                true, top.getFile(), top.getLine()));
    }

    // A return in the recursive method that doesn't call it again, in a call that never called
    // itself either: the base case. (A return node; after the call went down the tree is not one.)
    // True if one was found. pauses: whether this one is a key moment.
    private boolean baseCase(Trace trace, int step, Snapshot now, FrameSnapshot top, CodeFacts.MethodFact method,
                             CodeFacts facts, List<Moment> into, boolean pauses) {
        CodeFacts.ReturnFact back = facts.returnAt(top.getFile(), top.getLine());
        if (back == null || back.recursive() || recursedEarlier(trace, step, method.name())) {
            return false;
        }
        int depth = 0;
        for (FrameSnapshot frame : now.getFrames()) {
            if (frame.getMethodName().equals(method.name())) {
                depth++;
            }
        }
        String gives;
        if (back.expression() == null) {
            gives = "it stops here without calling itself.";
        } else {
            String result = Phrases.withValues(back.expression(), method.name(), "", top.getLocals());
            gives = "it returns " + (result == null ? back.expression() : result) + " without calling itself.";
        }
        into.add(new Moment(step, "base-case", "Base case",
                Phrases.call(top, method.parameters(), now) + " is a base case: " + gives + " "
                        + (depth > 1 ? (depth - 1) + (depth == 2 ? " call is" : " calls are") + " still waiting for "
                        + (method.returnsValue() ? "an answer." : "it to finish.") : "No call is waiting for it."),
                pauses, top.getFile(), top.getLine()));
        return true;
    }

    // Whether the call running at this step has already called itself (a deeper call of the same
    // method appeared since it started), found by looking back through the run.
    private static boolean recursedEarlier(Trace trace, int step, String method) {
        int depth = trace.get(step).getFrames().size();
        for (int s = step - 1; s >= 0; s--) {
            int size = trace.get(s).getFrames().size();
            if (size < depth) {
                return false; // back before this call started
            }
            if (size > depth && trace.get(s).getFrames().get(0).getMethodName().equals(method)) {
                return true;
            }
        }
        return false;
    }

    // The recursive calls that returned since the step before, oldest first, each with its value;
    // a caller that used the value shows how ("returns 3 * 2 = 6"). Null if none returned.
    private String returns(Snapshot before, Snapshot now, CodeFacts facts) {
        List<Exit> exits = now.getExits();
        List<FrameSnapshot> popped = before.getFrames();
        List<String> said = new ArrayList<>();
        String lastValue = null;
        String method = null;
        for (int k = 0; k < exits.size() && k < popped.size(); k++) {
            Exit exit = exits.get(k);
            FrameSnapshot frame = popped.get(k);
            if (!exit.method().equals(frame.getMethodName()) || !facts.isRecursive(exit.className(), exit.method())
                    || exit.returned() == null) {
                break; // only a recursive method's returns, matched to the frames they left
            }
            CodeFacts.MethodFact fact = facts.methodAt(frame.getFile(), frame.getLine());
            List<String> parameters = fact == null ? List.of() : fact.parameters();
            String value = Phrases.value(exit.returned(), now);
            String how = null;
            CodeFacts.ReturnFact back = facts.returnAt(frame.getFile(), frame.getLine());
            if (back != null && back.recursive() && back.expression() != null && lastValue != null && exit.method().equals(method)
                    && callsIn(back.expression(), exit.method()) == 1) {
                how = Phrases.withValues(back.expression(), exit.method(), lastValue, frame.getLocals());
            }
            said.add(Phrases.call(frame, parameters, before) + " returns " + (how != null && !how.equals(value) ? how + " = " : "") + value);
            lastValue = value;
            method = exit.method();
        }
        if (said.isEmpty()) {
            return null;
        }
        String text = String.join(", then ", said) + ".";
        if (!now.getFrames().isEmpty() && !now.getFrames().get(0).getMethodName().equals(method)) {
            text += " The answer, " + lastValue + ", goes back to " + now.getFrames().get(0).getMethodName() + "().";
        }
        return text;
    }

    // How many times an expression calls the method: with more than one (fib(n - 1) + fib(n - 2)),
    // a returned value can't honestly be put in for one of them.
    private static int callsIn(String expression, String method) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\b" + java.util.regex.Pattern.quote(method) + "\\s*\\(")
                .matcher(expression);
        int count = 0;
        while (m.find()) {
            count++;
        }
        return count;
    }
}
