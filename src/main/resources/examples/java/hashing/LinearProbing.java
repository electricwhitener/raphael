// A hash table with linear probing: each key goes in the slot its hash picks; if that slot is
// taken, it tries the next one, and the next, wrapping round. -1 marks an empty slot.
public class LinearProbing {
    static int[] table = {-1, -1, -1, -1, -1, -1, -1};

    static void insert(int key) {
        int slot = key % table.length;
        while (table[slot] != -1) {
            slot = (slot + 1) % table.length;      // taken: try the next one
        }
        table[slot] = key;
    }

    static int find(int key) {
        int slot = key % table.length;
        while (table[slot] != -1) {
            if (table[slot] == key) {
                return slot;
            }
            slot = (slot + 1) % table.length;
        }
        return -1;
    }

    public static void main(String[] args) {
        for (int key : new int[] {10, 3, 17, 24, 5}) {
            insert(key);               // 10, 3, 17 and 24 all want slot 3
        }
        System.out.println("24 is in slot " + find(24));
    }
}
