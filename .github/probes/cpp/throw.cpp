#include <stdexcept>
int check(int x) {
    if (x < 0) throw std::out_of_range("negative");
    return x;
}
int main() {
    int r = 0;
    try {
        r = check(-1);
    } catch (const std::exception &e) {
        r = 1;
    }
    return r - 1;
}
