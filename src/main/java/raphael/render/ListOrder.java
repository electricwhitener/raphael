package raphael.render;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import raphael.detect.LinkedListStructure;

// Remembers a left-to-right order for linked-list nodes across the whole run, so a node keeps
// its place from step to step:
//   - when a node first becomes part of a chain, it goes right after the node before it
//     (or right before the node after it, if it's the new head);
//   - a node on its own (just created, not linked yet) gets no place yet and waits at the end.
// The result: when a list is reversed, the nodes stay put and only the arrows turn around.
// The whole run is learned first, step by step, and the pictures are built later, in any order
// (see SceneList). So each node also remembers the step it got its place, and arrange() shows
// the order as it was at the step being drawn: before that step, the node is still waiting.
// OOP: encapsulation. The remembered order is private; callers only learn(), showStep() and arrange().
public class ListOrder {

    private final List<Long> order = new ArrayList<>();
    private final Map<Long, Integer> placedAt = new HashMap<>(); // node id → the step it got its place
    private Map<Long, Integer> rank; // node id → its index in order; made again after learning
    private int step = Integer.MAX_VALUE; // arrange() shows the order as it was at this step

    public void learn(LinkedListStructure list, int atStep) {
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
                placedAt.put(id, atStep);
                rank = null;
            }
        }
    }

    // The step the next pictures are for.
    public void showStep(int step) {
        this.step = step;
    }

    // The given ids from left to right: nodes with a place first, in that order, then the rest by id.
    public List<Long> arrange(Collection<Long> ids) {
        if (rank == null) {
            rank = new HashMap<>();
            for (int i = 0; i < order.size(); i++) {
                rank.put(order.get(i), i);
            }
        }
        List<Long> placed = new ArrayList<>();
        List<Long> waiting = new ArrayList<>();
        for (long id : ids) {
            Integer since = placedAt.get(id);
            if (since != null && since <= step) {
                placed.add(id);
            } else {
                waiting.add(id);
            }
        }
        placed.sort(Comparator.comparingInt(rank::get));
        Collections.sort(waiting);
        placed.addAll(waiting);
        return placed;
    }
}
