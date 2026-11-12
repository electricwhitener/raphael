#include <iostream>
using namespace std;

// Insert with recursion: a smaller key goes into the left subtree, a bigger one into the right.
struct Node {
    int key;
    Node *left = nullptr;
    Node *right = nullptr;
    Node(int k) : key(k) {}
};

Node *insert(Node *root, int key) {
    if (root == nullptr) {
        return new Node(key);
    }
    if (key < root->key) {
        root->left = insert(root->left, key);
    } else {
        root->right = insert(root->right, key);
    }
    return root;
}

void inorder(Node *root) {
    if (root == nullptr) {
        return;
    }
    inorder(root->left);
    cout << root->key << " ";
    inorder(root->right);
}

int main() {
    Node *root = nullptr;
    int keys[] = {8, 3, 10, 1, 6};
    for (int k : keys) {
        root = insert(root, k);
    }
    inorder(root);
    cout << endl;
    return 0;
}
