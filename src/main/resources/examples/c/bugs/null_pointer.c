#include <stdio.h>
#include <stdlib.h>

struct Node {
    int val;
    struct Node *next;
};

int main(void) {
    struct Node *first = malloc(sizeof *first);
    first->val = 1;
    first->next = NULL;
    // The bug: there's no second node, so first->next is NULL.
    int second = first->next->val;
    printf("%d\n", second);
    return 0;
}
