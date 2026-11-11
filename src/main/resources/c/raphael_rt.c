/* Raphael's runtime shim, linked into every C program Raphael runs (compiled without -g, so the
   debugger never steps into it).
   - When Raphael names them (RAPHAEL_STDIN, RAPHAEL_STDOUT), the program reads its input from a
     file and writes its output (stdout and stderr) to another, so the debugger's own messages
     never mix with what the program prints.
   - stdout's buffer is turned off, so what the program prints shows up at the step that printed
     it, even before a crash. (On Windows, line buffering is full buffering, so the buffer is
     turned off rather than set to lines.)
   Stage 14, step 5 adds the malloc and free table. */
#include <stdio.h>
#include <stdlib.h>
#ifdef _WIN32
#include <io.h>
#define dup2 _dup2
#define fileno _fileno
#else
#include <unistd.h>
#endif

__attribute__((constructor)) static void raphael_start(void) {
    const char *in = getenv("RAPHAEL_STDIN");
    const char *out = getenv("RAPHAEL_STDOUT");
    if (in != NULL && freopen(in, "r", stdin) == NULL) {
        fclose(stdin); /* the input file is missing: reading finds the end at once */
    }
    if (out != NULL && freopen(out, "w", stdout) != NULL) {
        dup2(fileno(stdout), fileno(stderr)); /* one file, in the order things were printed */
    }
    setvbuf(stdout, NULL, _IONBF, 0);
}
