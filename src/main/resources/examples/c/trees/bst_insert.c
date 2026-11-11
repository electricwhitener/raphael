#include <stdio.h>
#include <stdlib.h>

struct Node {
    int key;
    struct Node *left;
    struct Node *right;
};

struct Node *insert(struct Node *root, int key) {
    if (root == NULL) {
        struct Node *n = malloc(sizeof *n);
        n->key = key;
        n->left = NULL;
        n->right = NULL;
        return n;
    }
    if (key < root->key) {
        root->left = insert(root->left, key);
    } else {
        root->right = insert(root->right, key);
    }
    return root;
}

void print_in_order(struct Node *root) {
    if (root == NULL) {
        return;
    }
    print_in_order(root->left);
    printf("%d ", root->key);
    print_in_order(root->right);
}

int main(void) {
    int keys[] = {50, 30, 70, 20, 40, 60};
    struct Node *root = NULL;
    for (int i = 0; i < 6; i++) {
        root = insert(root, keys[i]);
    }
    print_in_order(root);
    printf("\n");
    return 0;
}
