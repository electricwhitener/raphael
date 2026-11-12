#include <iostream>
#include <map>
#include <string>
using namespace std;

// A map keeps its keys in order. counts[word]++ makes the word's count (0) the first time it's seen.
int main() {
    string words[] = {"to", "be", "or", "not", "to", "be"};
    map<string, int> counts;
    for (const string &w : words) {
        counts[w]++;
    }
    for (const auto &entry : counts) {
        cout << entry.first << " " << entry.second << endl;
    }
    return 0;
}
