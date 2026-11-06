// A min-heap kept in an array: the children of index i are at 2i+1 and 2i+2, and no child is
// smaller than its parent, so the smallest value is always at index 0.
// Insert: add at the end and sift it up. Remove: move the last value to the top and sift it down.
public class MinHeap {
    static int[] heap = new int[8];
    static int size = 0;

    static void insert(int value) {
        int i = size++;
        heap[i] = value;
        while (i > 0 && heap[(i - 1) / 2] > heap[i]) {   // smaller than its parent?
            swap(i, (i - 1) / 2);
            i = (i - 1) / 2;
        }
    }

    static int removeMin() {
        int min = heap[0];
        heap[0] = heap[--size];
        heap[size] = 0;
        int i = 0;
        while (true) {
            int smallest = i;
            int left = 2 * i + 1;
            int right = 2 * i + 2;
            if (left < size && heap[left] < heap[smallest]) {
                smallest = left;
            }
            if (right < size && heap[right] < heap[smallest]) {
                smallest = right;
            }
            if (smallest == i) {
                return min;
            }
            swap(i, smallest);
            i = smallest;
        }
    }

    static void swap(int a, int b) {
        int tmp = heap[a];
        heap[a] = heap[b];
        heap[b] = tmp;
    }

    public static void main(String[] args) {
        for (int v : new int[] {7, 3, 9, 1, 5}) {
            insert(v);
        }
        System.out.println(removeMin() + " " + removeMin() + " " + removeMin());
    }
}
