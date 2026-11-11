// A hash table with chaining: a key's hash picks its bucket, and each bucket is a linked list
// of the entries that landed there. Looking a key up only searches one short list.
public class HashChaining {
    static class Entry {
        String key;
        int value;
        Entry next;

        Entry(String key, int value, Entry next) {
            this.key = key;
            this.value = value;
            this.next = next;
        }
    }

    static Entry[] buckets = new Entry[4];

    static int bucketOf(String key) {
        return Math.abs(key.hashCode()) % buckets.length;
    }

    static void put(String key, int value) {
        int b = bucketOf(key);
        for (Entry e = buckets[b]; e != null; e = e.next) {
            if (e.key.equals(key)) {
                e.value = value;       // already there: update it
                return;
            }
        }
        buckets[b] = new Entry(key, value, buckets[b]);    // add at the front of the chain
    }

    static int get(String key) {
        for (Entry e = buckets[bucketOf(key)]; e != null; e = e.next) {
            if (e.key.equals(key)) {
                return e.value;
            }
        }
        return -1;
    }

    public static void main(String[] args) {
        put("cat", 3);
        put("dog", 5);
        put("owl", 2);
        put("bee", 7);
        put("fox", 4);
        put("dog", 6);
        System.out.println("dog = " + get("dog") + ", emu = " + get("emu"));
    }
}
