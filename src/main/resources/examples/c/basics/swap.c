#include <stdio.h>

// Gets copies of the numbers: swapping them changes nothing outside.
void swap_copies(int a, int b) {
    int t = a;
    a = b;
    b = t;
}

// Gets the addresses: *a and *b are the caller's own variables.
void swap(int *a, int *b) {
    int t = *a;
    *a = *b;
    *b = t;
}

int main(void) {
    int x = 1;
    int y = 2;
    swap_copies(x, y);
    printf("after swap_copies: x=%d y=%d\n", x, y);
    swap(&x, &y);
    printf("after swap: x=%d y=%d\n", x, y);
    return 0;
}
