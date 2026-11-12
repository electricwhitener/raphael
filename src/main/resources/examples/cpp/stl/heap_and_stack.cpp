#include <iostream>
#include <queue>
#include <stack>
using namespace std;

// A priority_queue keeps the biggest item on top: its items are a heap, a tree kept in an array,
// where each parent is at least as big as its children. A stack gives back the last item pushed.
int main() {
    priority_queue<int> pq;
    pq.push(3);
    pq.push(9);
    pq.push(5);
    pq.push(1);
    stack<int> st;
    st.push(1);
    st.push(2);
    st.push(3);
    cout << pq.top() << " " << st.top() << endl;
    pq.pop();
    st.pop();
    cout << pq.top() << " " << st.top() << endl;
    return 0;
}
