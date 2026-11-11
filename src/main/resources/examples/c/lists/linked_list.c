#include <stdio.h>
#include <stdlib.h>

struct Node {
    int val;
    struct Node *next;
};

// A new node in front of the list; the new node is the list's new head.
struct Node *push(struct Node *head, int val) {
    struct Node *n = malloc(sizeof *n);
    n->val = val;
    n->next = head;
    return n;
}

int main(void) {
    struct Node *head = NULL;
    for (int i = 1; i <= 4; i++) {
        head = push(head, i * 10);
    }
    for (struct Node *curr = head; curr != NULL; curr = curr->next) {
        printf("%d ", curr->val);
    }
    printf("\n");
    return 0;
}
