#include <iostream>
using namespace std;

// int &a is a reference: another name for the caller's variable, so swap can change x and y.
// Without the &, swap would get copies and x and y would stay as they were.
void swapBoth(int &a, int &b) {
    int t = a;
    a = b;
    b = t;
}

int main() {
    int x = 3;
    int y = 8;
    swapBoth(x, y);
    cout << x << " " << y << endl;
    return 0;
}
