#include <stdio.h>
#include <stdlib.h>

struct Node {
    int val;
    struct Node *next;
};

struct Node *push(struct Node *head, int val) {
    struct Node *n = malloc(sizeof *n);
    n->val = val;
    n->next = head;
    return n;
}

int sum(struct Node *head) {
    if (head == NULL) {
        return 0;
    }
    return head->val + sum(head->next);
}

int main(void) {
    struct Node *head = NULL;
    for (int i = 1; i <= 4; i++) {
        head = push(head, i * 10);
    }
    int arr[5] = {5, 3, 8, 1, 9};
    printf("sum %d\n", sum(head));
    return 0;
}
