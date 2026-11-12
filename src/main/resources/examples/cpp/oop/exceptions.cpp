#include <iostream>
#include <stdexcept>
#include <vector>
using namespace std;

// at() checks the index and throws out_of_range; the catch below gets it, and the program goes on.
int main() {
    vector<int> marks = {70, 85, 90};
    try {
        cout << marks.at(1) << endl;
        cout << marks.at(5) << endl;
    } catch (const out_of_range &e) {
        cout << "no mark there" << endl;
    }
    cout << "done" << endl;
    return 0;
}
