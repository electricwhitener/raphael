/* Raphael's runtime shim, linked into every C program Raphael runs (compiled without -g, so the
   debugger never steps into it). For now it only turns off stdout's buffer, so what the program
   prints shows up at the step that printed it, even before a crash. (On Windows, line buffering
   is full buffering, so the buffer is turned off rather than set to lines.)
   Stage 14, step 5 adds the malloc and free table. */
#include <stdio.h>

__attribute__((constructor)) static void raphael_start(void) {
    setvbuf(stdout, NULL, _IONBF, 0);
}
