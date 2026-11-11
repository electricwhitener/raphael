// Heap sort: arrange the array as a max-heap, where the children of index i are at 2i+1 and
// 2i+2 and no child is bigger than its parent. The biggest value is then at index 0: swap it
// to the end, shrink the heap by one, and repair it.
public class HeapSort {
    static void siftDown(int[] arr, int i, int size) {
        while (true) {
            int largest = i;
            int left = 2 * i + 1;
            int right = 2 * i + 2;
            if (left < size && arr[left] > arr[largest]) {
                largest = left;
            }
            if (right < size && arr[right] > arr[largest]) {
                largest = right;
            }
            if (largest == i) {
                return;                // the parent is the biggest: done
            }
            swap(arr, i, largest);
            i = largest;
        }
    }

    static void swap(int[] arr, int a, int b) {
        int tmp = arr[a];
        arr[a] = arr[b];
        arr[b] = tmp;
    }

    public static void main(String[] args) {
        int[] arr = {4, 10, 3, 5, 1, 8};
        for (int i = arr.length / 2 - 1; i >= 0; i--) {
            siftDown(arr, i, arr.length);      // build the heap
        }
        for (int end = arr.length - 1; end > 0; end--) {
            swap(arr, 0, end);                 // the biggest goes to the end
            siftDown(arr, 0, end);
        }
        System.out.println(java.util.Arrays.toString(arr));
    }
}
