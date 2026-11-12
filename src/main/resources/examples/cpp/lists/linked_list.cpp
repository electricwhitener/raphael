#include <iostream>
using namespace std;

// new makes a node that lives until delete gives it back; each node points at the next one.
struct Node {
    int val;
    Node *next;
    Node(int v, Node *n) : val(v), next(n) {}
};

int main() {
    Node *head = nullptr;
    for (int i = 3; i >= 1; i--) {
        head = new Node(i, head); // the new node goes in front
    }
    for (Node *p = head; p != nullptr; p = p->next) {
        cout << p->val << " ";
    }
    cout << endl;
    while (head != nullptr) {
        Node *gone = head;
        head = head->next;
        delete gone;
    }
    return 0;
}
