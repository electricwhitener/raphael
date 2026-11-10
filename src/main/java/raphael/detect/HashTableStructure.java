package raphael.detect;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import raphael.model.ArrayNode;
import raphael.model.ObjectNode;
import raphael.model.ReferenceValue;
import raphael.model.Value;

// A hash table with chaining: an array of buckets, each holding the first node of a linked
// list (Entry[] buckets, each Entry with a next). Drawn as textbooks draw it: the buckets in a
// column, and each bucket's chain hanging to its right.
// OOP: inheritance (LinkedStructure → HashTableStructure), so it keeps its nodes like a list does.
public class HashTableStructure extends LinkedStructure {

    private final ArrayNode table;
    private final String nextField;

    public HashTableStructure(ArrayNode table, Map<Long, ObjectNode> nodes, String nextField) {
        super(nodes);
        this.table = table;
        this.nextField = nextField;
    }

    public ArrayNode getTable() {
        return table;
    }

    // Bucket i's chain, first node first (empty for an empty bucket). A node already met (a
    // chain that loops, or two buckets sharing a node) ends the chain.
    public List<Long> chain(int bucket, Set<Long> seen) {
        List<Long> chain = new ArrayList<>();
        Value head = table.getElements().get(bucket);
        long id = head instanceof ReferenceValue && getNodes().containsKey(((ReferenceValue) head).id()) ? ((ReferenceValue) head).id() : -1;
        while (id != -1 && seen.add(id)) {
            chain.add(id);
            id = follow(id, nextField);
        }
        return chain;
    }

    public long getNext(long id) {
        return follow(id, nextField);
    }

    // Every node reachable from a bucket.
    public static Set<Long> reachable(ArrayNode table, Map<Long, ObjectNode> nodes, String nextField) {
        Set<Long> found = new HashSet<>();
        for (Value head : table.getElements()) {
            long id = head instanceof ReferenceValue ? ((ReferenceValue) head).id() : -1;
            while (nodes.containsKey(id) && found.add(id)) {
                Value next = nodes.get(id).getField(nextField);
                id = next instanceof ReferenceValue ? ((ReferenceValue) next).id() : -1;
            }
        }
        return found;
    }

    @Override
    public Set<Long> getObjectIds() {
        Set<Long> ids = new HashSet<>(super.getObjectIds());
        ids.add(table.getId());
        return ids;
    }

    @Override
    public String getKind() {
        return "hash table";
    }
}
