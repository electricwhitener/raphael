package raphael.render;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import raphael.model.Exit;
import raphael.model.FrameSnapshot;
import raphael.model.Snapshot;
import raphael.model.Trace;
import raphael.model.Value;

// Every call a run made, as a tree: who called whom, with what, and what each gave back. Rebuilt
// exactly from the steps: each call's first line is a step of its own (it has a breakpoint), and
// the returns between two steps are that step's Exit list, in order, from the top of the stack.
// So fib(1) and fib(0) are told apart even when both return within one line.
// OOP: a class that hides how the tree is worked out; its nested Call class is the tree's node.
public class CallTree {

    // One call: its method, the arguments it started with, its children, and when it ran.
    public static class Call {
        private final String className;
        private final String method;
        private final Map<String, Value> arguments;
        private final Snapshot startedIn;
        private final Call parent;
        private final List<Call> children = new ArrayList<>();
        private final int start;
        private int end = -1;             // the step at which it had returned, or -1
        private Value returned;

        Call(FrameSnapshot frame, Snapshot step, int start, Call parent) {
            this.className = frame.getClassName();
            this.method = frame.getMethodName();
            this.arguments = frame.getLocals(); // on a method's first line, its only variables are its parameters
            this.startedIn = step;
            this.start = start;
            this.parent = parent;
        }

        public String getMethod() {
            return method;
        }

        public Map<String, Value> getArguments() {
            return arguments;
        }

        public Snapshot getStartedIn() {
            return startedIn;
        }

        public Call getParent() {
            return parent;
        }

        public List<Call> getChildren() {
            return children;
        }

        public int getStart() {
            return start;
        }

        public int getEnd() {
            return end;
        }

        public Value getReturned() {
            return returned;
        }

        // Whether the call had started and not yet returned at this step.
        public boolean isActiveAt(int step) {
            return start <= step && (end < 0 || step < end);
        }
    }

    private final List<Call> roots = new ArrayList<>();
    private final Map<Integer, Call> topAt = new HashMap<>(); // the running call at each step
    private boolean complete = true;

    public CallTree(Trace trace) {
        List<Call> stack = new ArrayList<>(); // the same calls as the step's frames, main first
        for (int s = 0; s < trace.size(); s++) {
            Snapshot now = trace.get(s);
            for (Exit exit : now.getExits()) {
                if (!stack.isEmpty()) {
                    Call done = stack.remove(stack.size() - 1);
                    done.end = s;
                    done.returned = exit.returned();
                }
            }
            List<FrameSnapshot> frames = now.getFrames(); // the running call first
            if (frames.size() >= 32) {
                complete = false; // only the newest 32 frames are recorded, so the tree can't be exact
            }
            int same = 0; // how many calls, from main up, are still the step's frames
            while (same < stack.size() && same < frames.size()
                    && stack.get(same).method.equals(frames.get(frames.size() - 1 - same).getMethodName())) {
                same++;
            }
            while (stack.size() > same) { // left without a return: an exception went through it
                Call left = stack.remove(stack.size() - 1);
                left.end = s;
            }
            while (stack.size() < frames.size()) {
                FrameSnapshot frame = frames.get(frames.size() - 1 - stack.size());
                Call parent = stack.isEmpty() ? null : stack.get(stack.size() - 1);
                Call call = new Call(frame, now, s, parent);
                if (parent == null) {
                    roots.add(call);
                } else {
                    parent.children.add(call);
                }
                stack.add(call);
            }
            if (!stack.isEmpty()) {
                topAt.put(s, stack.get(stack.size() - 1));
            }
        }
    }

    // Whether every step's whole stack was recorded (it wasn't past 32 frames).
    public boolean isComplete() {
        return complete;
    }

    public List<Call> getRoots() {
        return roots;
    }

    // The call running at this step, or null.
    public Call runningAt(int step) {
        return topAt.get(step);
    }

    // The calls of methods that call themselves more than once in one call (fib, Hanoi's move):
    // the ones a stack can't show well, because it forgets the calls that already returned.
    public List<String> branchingMethods() {
        List<String> found = new ArrayList<>();
        List<Call> todo = new ArrayList<>(roots);
        while (!todo.isEmpty()) {
            Call call = todo.remove(todo.size() - 1);
            int self = 0;
            for (Call child : call.children) {
                if (child.method.equals(call.method) && child.className.equals(call.className)) {
                    self++;
                }
                todo.add(child);
            }
            if (self >= 2 && !found.contains(call.method)) {
                found.add(call.method);
            }
        }
        return found;
    }
}
