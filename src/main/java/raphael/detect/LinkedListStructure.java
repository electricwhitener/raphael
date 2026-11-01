package raphael.detect;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import raphael.model.ObjectNode;

// All the nodes of one linked-list class. While a list is being changed (e.g. reversed) its nodes
// can form several chains at once, like prev: 2 → 1 and curr: 3 → 4, so this keeps every chain.
// OOP: inheritance (LinkedStructure → LinkedListStructure: multilevel) and overriding.
public class LinkedListStructure extends LinkedStructure {

    private final String nextField;
    private final String prevField; // null for a singly linked list
    private final List<List<Long>> chains;

    public LinkedListStructure(Map<Long, ObjectNode> nodes, String nextField, String prevField) {
        super(nodes);
        this.nextField = nextField;
        this.prevField = prevField;
        this.chains = findChains();
    }

    public long getNext(long id) {
        return follow(id, nextField);
    }

    public long getPrev(long id) {
        return prevField == null ? -1 : follow(id, prevField);
    }

    public boolean isDoublyLinked() {
        return prevField != null;
    }

    // Each chain lists node ids from a head, following next. Every node is in exactly one chain.
    public List<List<Long>> getChains() {
        return chains;
    }

    @Override
    public String getKind() {
        return isDoublyLinked() ? "doubly linked list" : "linked list";
    }

    // A head is a node that no other node's next points at. A cycle with no head starts at its
    // smallest id. The "visited" set stops a cycle from looping forever.
    private List<List<Long>> findChains() {
        Set<Long> pointedAt = new HashSet<>();
        for (long id : getNodes().keySet()) {
            long next = getNext(id);
            if (next != -1 && next != id) {
                pointedAt.add(next);
            }
        }
        List<Long> starts = new ArrayList<>();
        for (long id : getNodes().keySet()) {
            if (!pointedAt.contains(id)) {
                starts.add(id);
            }
        }
        starts.addAll(getNodes().keySet()); // any nodes left over (cycles) start a chain too

        List<List<Long>> result = new ArrayList<>();
        Set<Long> visited = new HashSet<>();
        for (long start : starts) {
            if (visited.contains(start)) {
                continue;
            }
            List<Long> chain = new ArrayList<>();
            for (long id = start; id != -1 && visited.add(id); id = getNext(id)) {
                chain.add(id);
            }
            result.add(chain);
        }
        return result;
    }
}
