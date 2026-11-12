#include <algorithm>
#include <iostream>
#include <vector>
using namespace std;

// A vector grows as you push_back; sort puts its items in order.
int main() {
    vector<int> v;
    v.push_back(5);
    v.push_back(2);
    v.push_back(9);
    v.push_back(1);
    sort(v.begin(), v.end());
    for (int x : v) {
        cout << x << " ";
    }
    cout << endl;
    return 0;
}
