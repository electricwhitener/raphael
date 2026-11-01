package raphael.render;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import raphael.detect.LinkedListStructure;

// Remembers a left-to-right order for linked-list nodes across the whole run, so a node keeps
// its place from step to step:
//   - when a node first becomes part of a chain, it goes right after the node before it
//     (or right before the node after it, if it's the new head);
//   - a node on its own (just created, not linked yet) gets no place yet and waits at the end.
// The result: when a list is reversed, the nodes stay put and only the arrows turn around.
// OOP: encapsulation. The remembered order is private; callers only learn() and arrange().
public class ListOrder {

    private final List<Long> order = new ArrayList<>();

    public void learn(LinkedListStructure list) {
        for (List<Long> chain : list.getChains()) {
            if (chain.size() < 2) {
                continue;
            }
            for (int i = 0; i < chain.size(); i++) {
                long id = chain.get(i);
                if (order.contains(id)) {
                    continue;
                }
                if (i > 0) {
                    order.add(order.indexOf(chain.get(i - 1)) + 1, id);
                } else if (order.contains(chain.get(1))) {
                    order.add(order.indexOf(chain.get(1)), id);
                } else {
                    order.add(id);
                }
            }
        }
    }

    // The given ids from left to right: nodes with a place first, in that order, then the rest by id.
    public List<Long> arrange(Collection<Long> ids) {
        List<Long> placed = new ArrayList<>();
        List<Long> waiting = new ArrayList<>();
        for (long id : ids) {
            if (order.contains(id)) {
                placed.add(id);
            } else {
                waiting.add(id);
            }
        }
        placed.sort(Comparator.comparingInt(order::indexOf));
        Collections.sort(waiting);
        placed.addAll(waiting);
        return placed;
    }
}
