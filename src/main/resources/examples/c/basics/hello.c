#include <stdio.h>

int main(void) {
    int apples = 3;
    int pears = 4;
    int total = apples + pears;
    double share = total / 2.0;
    char grade = 'A';
    printf("total %d, share %.1f, grade %c\n", total, share, grade);
    return 0;
}
